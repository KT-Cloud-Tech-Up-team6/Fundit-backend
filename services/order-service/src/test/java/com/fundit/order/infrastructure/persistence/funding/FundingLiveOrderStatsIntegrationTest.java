package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;
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
 * 집계 쿼리는 {@code FILTER}·{@code count(DISTINCT)}·따옴표 별칭처럼 PostgreSQL에서만 재현되는
 * 요소로 되어 있어 실제 DB로 검증한다(단위 테스트로는 매핑이 깨져도 드러나지 않는다).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class FundingLiveOrderStatsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingRepository fundingRepository;
    @Autowired
    private FundingJpaRepository fundingJpaRepository;

    private static final long LIVE_SESSION_ID = 4_242L;

    private Funding order(long liveSessionId, FundingStatus status, long unitPrice, int quantity) {
        Funding funding = Funding.create(UUID.randomUUID(), UUID.randomUUID(), "프로젝트",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                3_000L, List.of(new FundingLineItem(null, 1L, "리워드", quantity, unitPrice, List.of())),
                Instant.now().plusSeconds(3600), null, null, liveSessionId);
        Funding saved = fundingRepository.save(funding);
        return status == FundingStatus.PENDING ? saved : fundingRepository.save(saved.toBuilder().status(status).build());
    }

    @Test
    void 결제완료와_미결제를_나눠_집계한다() {
        // given
        order(LIVE_SESSION_ID, FundingStatus.FUNDING_IN_PROGRESS, 10_000L, 2);
        order(LIVE_SESSION_ID, FundingStatus.GOAL_ACHIEVED, 5_000L, 1);
        order(LIVE_SESSION_ID, FundingStatus.PENDING, 30_000L, 1);

        // when
        LiveOrderStatsProjection stats = fundingJpaRepository.findLiveOrderStats(LIVE_SESSION_ID);

        // then — 금액은 쿠폰 할인 반영 전 리워드 합산액(배송비 제외)
        assertThat(stats.getPaidCount()).isEqualTo(2);
        assertThat(stats.getPaidAmount()).isEqualTo(25_000L);
        assertThat(stats.getPendingCount()).isEqualTo(1);
        assertThat(stats.getPendingAmount()).isEqualTo(30_000L);
    }

    @Test
    void 취소_만료_건은_어느_쪽에도_섞이지_않는다() {
        // given
        order(LIVE_SESSION_ID, FundingStatus.CANCELLED_BY_MEMBER, 10_000L, 1);
        order(LIVE_SESSION_ID, FundingStatus.PAYMENT_EXPIRED, 10_000L, 1);

        // when
        LiveOrderStatsProjection stats = fundingJpaRepository.findLiveOrderStats(LIVE_SESSION_ID);

        // then
        assertThat(stats.getPaidCount()).isZero();
        assertThat(stats.getPaidAmount()).isZero();
        assertThat(stats.getPendingCount()).isZero();
        assertThat(stats.getPendingAmount()).isZero();
    }

    @Test
    void 다른_방송의_주문은_집계에_들어오지_않는다() {
        // given
        order(LIVE_SESSION_ID, FundingStatus.PENDING, 10_000L, 1);
        order(LIVE_SESSION_ID + 1, FundingStatus.PENDING, 99_000L, 1);

        // when
        LiveOrderStatsProjection stats = fundingJpaRepository.findLiveOrderStats(LIVE_SESSION_ID);

        // then
        assertThat(stats.getPendingCount()).isEqualTo(1);
        assertThat(stats.getPendingAmount()).isEqualTo(10_000L);
    }
}
