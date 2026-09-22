package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 옵션이 2개 그룹(색상+사이즈)인 라인은 funding_line_item_options에 행이 2개 생겨, 예전 쿼리처럼
 * 그 값만으로 GROUP BY하면 리워드 총 판매량을 옵션값 행 합산으로 구할 때 실제보다 부풀려졌다.
 * 리워드 전체 합계(optionValueId=null) 행이 부풀려지지 않는지 실제 DB로 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class FundingLineItemJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingRepository fundingRepository;
    @Autowired
    private FundingLineItemJpaRepository fundingLineItemJpaRepository;
    @Autowired
    private FundingJpaRepository fundingJpaRepository;

    @Test
    void 결제완료_펀딩만_배치_대상_projectPublicId로_잡힌다() {
        // given — cross-service ID 통일(#69) 이후 레거시 project_id(Long)는 신규 펀딩에 채워지지
        // 않으므로, 그 컬럼으로 조회하면 이 테스트가 항상 실패해야 정상이다(회귀 방지).
        UUID countableProjectId = UUID.randomUUID();
        UUID pendingOnlyProjectId = UUID.randomUUID();
        FundingLineItem lineItem = new FundingLineItem(null, 1L, "리워드", 1, 10_000L, List.of());

        Funding countable = fundingRepository.save(Funding.create(UUID.randomUUID(), countableProjectId, "프로젝트A",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(lineItem), Instant.now().plusSeconds(3600), null, null));
        countable.markPaymentCompleted();
        fundingRepository.save(countable);

        fundingRepository.save(Funding.create(UUID.randomUUID(), pendingOnlyProjectId, "프로젝트B",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(lineItem), Instant.now().plusSeconds(3600), null, null));

        // when
        List<UUID> result = fundingJpaRepository.findDistinctProjectPublicIdsWithCountableFundings();

        // then — 결제완료(FUNDING_IN_PROGRESS)만 잡히고, 아직 PENDING인 프로젝트는 제외된다
        assertThat(result).contains(countableProjectId).doesNotContain(pendingOnlyProjectId);
    }

    @Test
    void 옵션_그룹이_여러개여도_리워드_전체합계는_부풀려지지_않는다() {
        // given
        UUID projectId = UUID.randomUUID();
        Long rewardId = 42L;
        FundingLineItemOption color = new FundingLineItemOption(null, 1L, "색상", 100L, "레드");
        FundingLineItemOption size = new FundingLineItemOption(null, 2L, "사이즈", 200L, "L");
        FundingLineItem lineItem = new FundingLineItem(null, rewardId, "리워드", 3, 10_000L, List.of(color, size));
        Funding funding = Funding.create(UUID.randomUUID(), projectId, "테스트 프로젝트",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(lineItem), Instant.now().plusSeconds(3600), null, null);
        Funding saved = fundingRepository.save(funding);
        saved.markPaymentCompleted();
        fundingRepository.save(saved);

        // when
        List<FundingLineItemJpaRepository.RewardStatProjection> rows =
                fundingLineItemJpaRepository.aggregateRewardStatsByProjectId(projectId);

        // then — 총 합계(optionValueId=null)는 라인 수량(3)이지 옵션 차원 수(2)만큼 곱해진 값이 아니다
        var totalRow = rows.stream().filter(r -> r.getOptionValueId() == null).findFirst().orElseThrow();
        assertThat(totalRow.getRewardId()).isEqualTo(rewardId);
        assertThat(totalRow.getTotalQuantity()).isEqualTo(3);
        assertThat(totalRow.getTotalAmount()).isEqualTo(30_000L);

        // 옵션값별 분해 행은 참고용 — 각각 라인 전체 수량을 그대로 보여주며 합산 대상이 아니다
        assertThat(rows).filteredOn(r -> r.getOptionValueId() != null)
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.getTotalQuantity()).isEqualTo(3));
    }
}
