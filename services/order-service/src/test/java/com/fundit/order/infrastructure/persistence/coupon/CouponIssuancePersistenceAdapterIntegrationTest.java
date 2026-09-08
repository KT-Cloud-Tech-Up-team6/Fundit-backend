package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** coupon_issuances는 coupons.coupon_code를 FK로 참조하므로 발급 전 쿠폰 템플릿을 먼저 저장한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CouponIssuancePersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CouponIssuanceRepository couponIssuanceRepository;
    @Autowired
    private CouponJpaRepository couponJpaRepository;

    private void seedCoupon(String code) {
        couponJpaRepository.save(CouponJpaEntity.builder()
                .couponCode(code).couponName("쿠폰")
                .discountType("AMOUNT").discountValue(1_000)
                .issuerType("PLATFORM").targetScope("ALL")
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel("GENERAL")
                .version(0).build());
    }

    @Test
    void 발급코드와_소유자로_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        seedCoupon("CODE1");
        couponIssuanceRepository.save(CouponIssuance.issue("CODE1", memberId));

        // when
        var found = couponIssuanceRepository.findByCouponCodeAndOwnerId("CODE1", memberId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(CouponIssuanceStatus.AVAILABLE);
    }

    @Test
    void 소유자별_상태로_필터링해_페이징_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        seedCoupon("CODE2");
        seedCoupon("CODE3");
        CouponIssuance used = CouponIssuance.issue("CODE2", memberId);
        used.markUsed(1L);
        couponIssuanceRepository.save(used);
        couponIssuanceRepository.save(CouponIssuance.issue("CODE3", memberId));

        // when
        var availablePage = couponIssuanceRepository.findByOwnerId(memberId, CouponIssuanceStatus.AVAILABLE,
                PageRequest.of(0, 20));

        // then
        assertThat(availablePage.getTotalElements()).isEqualTo(1);
        assertThat(availablePage.getContent().get(0).getCouponCode()).isEqualTo("CODE3");
    }

    @Test
    void 동일_쿠폰코드의_발급_개수를_센다() {
        // given
        UUID memberId = UUID.randomUUID();
        seedCoupon("CODE4");
        couponIssuanceRepository.save(CouponIssuance.issue("CODE4", memberId));

        // when & then
        assertThat(couponIssuanceRepository.countByCouponCodeAndOwnerId("CODE4", memberId)).isEqualTo(1);
        assertThat(couponIssuanceRepository.countByCouponCodeAndOwnerId("CODE4", UUID.randomUUID())).isZero();
    }
}
