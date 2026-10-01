package com.fundit.order.application.payment;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
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
 * #230 — 결제완료 직후 리워드 통계가 재집계돼 아웃박스에 쌓이는지 실제 DB로 검증한다.
 *
 * <p>통합으로만 재현되는 지점이라 단위 테스트로 대체할 수 없다: 재집계 쿼리가 네이티브라
 * {@code save()}로 바뀐 status가 같은 트랜잭션 안에서 플러시돼 보여야 한다. 호출 순서가
 * 뒤집히거나 플러시가 일어나지 않으면 집계가 0원으로 나와 이 테스트가 깨진다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class PaymentEventSyncServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PaymentEventSyncService paymentEventSyncService;
    @Autowired
    private FundingRepository fundingRepository;
    @Autowired
    private FundingRewardStatsEventOutboxJpaRepository rewardStatsOutboxRepository;

    @Test
    void 결제가_완료되면_그_자리에서_리워드_통계를_재집계해_아웃박스에_쌓는다() {
        // given — 아직 PENDING이라 집계 대상이 아닌 참여 건
        UUID projectId = UUID.randomUUID();
        Funding pending = fundingRepository.save(Funding.create(UUID.randomUUID(), projectId, "맛있는 과자",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(new FundingLineItem(null, 1L, "리워드", 2, 10_000L, List.of())),
                Instant.now().plusSeconds(3600), null, null, null));

        // when
        paymentEventSyncService.onPaymentCompleted(
                new PaymentEventListener.PaymentCompletedEvent(pending.getPublicId(), List.of(), Instant.now()));

        // then — 배치(매일 03:00)를 기다리지 않고 바로 적재된다
        List<FundingRewardStatsEventOutboxJpaEntity> unpublished =
                rewardStatsOutboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(unpublished).singleElement().satisfies(row -> {
            assertThat(row.getProjectId()).isEqualTo(projectId);
            assertThat(row.getParticipantCount()).isEqualTo(1);
            assertThat(row.getRewardStats()).singleElement().satisfies(stat -> {
                assertThat(stat.rewardId()).isEqualTo(1L);
                assertThat(stat.optionValueId()).isNull();
                assertThat(stat.purchasedQuantity()).isEqualTo(2);
                assertThat(stat.purchasedAmount()).isEqualTo(20_000L);
            });
        });
    }
}
