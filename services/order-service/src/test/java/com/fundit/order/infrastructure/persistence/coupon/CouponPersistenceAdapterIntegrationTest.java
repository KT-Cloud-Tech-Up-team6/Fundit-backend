package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** coupons.remaining_quantity/used_budget_amount 조건부 UPDATE(낙관적 락)를 검증한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CouponPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponJpaRepository jpaRepository;

    private void seed(String code, int remainingQuantity, long usedBudgetAmount, Long budgetLimit) {
        jpaRepository.save(CouponJpaEntity.builder()
                .couponCode(code).couponName("쿠폰")
                .discountType("AMOUNT").discountValue(1_000)
                .budgetLimit(budgetLimit).usedBudgetAmount(usedBudgetAmount)
                .issuerType("PLATFORM").targetScope("ALL")
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(remainingQuantity)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel("GENERAL")
                .version(0).build());
    }

    @Test
    void 재고가_있으면_차감에_성공하고_버전이_증가한다() {
        // given
        seed("CODE1", 5, 0, null);

        // when
        boolean result = couponRepository.decreaseRemainingQuantity("CODE1");

        // then
        assertThat(result).isTrue();
        Coupon updated = couponRepository.findByCouponCode("CODE1").orElseThrow();
        assertThat(updated.getRemainingQuantity()).isEqualTo(4);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void 소진됐으면_차감에_실패한다() {
        // given
        seed("CODE2", 0, 0, null);

        // when
        boolean result = couponRepository.decreaseRemainingQuantity("CODE2");

        // then
        assertThat(result).isFalse();
    }

    @Test
    void 사용예산을_반영하면_used_budget_amount가_증가한다() {
        // given
        seed("CODE3", 5, 1_000, 100_000L);

        // when
        boolean result = couponRepository.increaseUsedBudget("CODE3", 2_000);

        // then
        assertThat(result).isTrue();
        assertThat(couponRepository.findByCouponCode("CODE3").orElseThrow().getUsedBudgetAmount()).isEqualTo(3_000);
    }

    @Test
    void 쿠폰코드_목록으로_배치_조회한다() {
        // given
        seed("CODE4", 5, 0, null);
        seed("CODE5", 5, 0, null);

        // when
        var coupons = couponRepository.findByCouponCodeIn(java.util.List.of("CODE4", "CODE5", "MISSING"));

        // then
        assertThat(coupons).hasSize(2);
    }
}
