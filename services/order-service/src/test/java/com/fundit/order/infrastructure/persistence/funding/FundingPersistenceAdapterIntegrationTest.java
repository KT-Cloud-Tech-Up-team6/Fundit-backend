package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Funding 애그리거트(루트 + line_items + options 3개 플랫 테이블 수동 조합)의 JPA 배선을 검증한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class FundingPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingRepository fundingRepository;

    private Funding newFunding(UUID memberId, Long projectId, FundingStatus status, Instant paymentExpiresAt) {
        List<FundingLineItemOption> options = List.of(new FundingLineItemOption(null, 10L, "색상", 100L, "화이트"));
        List<FundingLineItem> lineItems = List.of(new FundingLineItem(null, 5L, "얼리버드 패키지", 2, 10_000L, options));
        Funding funding = Funding.create(memberId, projectId, "프로젝트",
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "101동"),
                3_000L, lineItems, paymentExpiresAt);
        if (status != FundingStatus.PENDING) {
            funding = funding.toBuilder().status(status).build();
        }
        return funding;
    }

    @Test
    void 생성하면_라인아이템과_옵션이_함께_저장된다() {
        // given
        UUID memberId = UUID.randomUUID();
        Funding funding = newFunding(memberId, 1L, FundingStatus.PENDING, Instant.now().plusSeconds(1800));

        // when
        Funding saved = fundingRepository.save(funding);

        // then
        Funding found = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        assertThat(found.getLineItems()).hasSize(1);
        FundingLineItem lineItem = found.getLineItems().get(0);
        assertThat(lineItem.rewardName()).isEqualTo("얼리버드 패키지");
        assertThat(lineItem.options()).singleElement()
                .extracting(FundingLineItemOption::optionValue).isEqualTo("화이트");
    }

    @Test
    void 상태만_바꿔_다시_저장해도_라인아이템이_중복_적재되지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        Funding saved = fundingRepository.save(newFunding(memberId, 1L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        Funding loaded = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();

        // when
        loaded.cancelByMember();
        fundingRepository.save(loaded);

        // then
        Funding reloaded = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        assertThat(reloaded.getLineItems()).hasSize(1);
    }

    @Test
    void 회원ID로_페이징_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        fundingRepository.save(newFunding(memberId, 1L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(memberId, 2L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), 3L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));

        // when
        var page = fundingRepository.findByMemberId(memberId, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 상태필터를_지정하면_해당_상태만_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        fundingRepository.save(newFunding(memberId, 1L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(memberId, 2L, FundingStatus.CANCELLED_BY_MEMBER, Instant.now().plusSeconds(1800)));

        // when
        var page = fundingRepository.findByMemberId(memberId, FundingStatus.CANCELLED_BY_MEMBER, PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
    }

    @Test
    void 결제만료시각이_지난_PENDING_건만_조회한다() {
        // given
        fundingRepository.save(newFunding(UUID.randomUUID(), 1L, FundingStatus.PENDING, Instant.now().minusSeconds(60)));
        fundingRepository.save(newFunding(UUID.randomUUID(), 2L, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), 3L, FundingStatus.CANCELLED_BY_MEMBER, Instant.now().minusSeconds(60)));

        // when
        List<Funding> targets = fundingRepository.findPendingExpiredBefore(Instant.now());

        // then
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getProjectId()).isEqualTo(1L);
    }

    @Test
    void 프로젝트의_활성_참여건만_조회한다() {
        // given
        Long projectId = 7L;
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.CANCELLED_BY_MEMBER, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.PAYMENT_EXPIRED, Instant.now().plusSeconds(1800)));

        // when
        List<Funding> active = fundingRepository.findActiveByProjectId(projectId);

        // then
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo(FundingStatus.PENDING);
    }
}
