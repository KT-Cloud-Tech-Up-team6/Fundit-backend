package com.fundit.order.domain.coupon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CouponUnitTest {

    private Coupon.CouponBuilder base() {
        return Coupon.builder()
                .id(1L).couponCode("PJT1-ABCD").couponName("쿠폰")
                .issuerType(IssuerType.PLATFORM)
                .targetScope(CouponTargetScope.ALL)
                .minFundingAmount(0)
                .perMemberLimit(1)
                .remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .issueChannel(IssueChannel.GENERAL)
                .version(0);
    }

    @Nested
    class 할인액_계산 {

        @Test
        void RATE는_비율만큼_할인하고_최대할인한도로_캡핑한다() {
            // given
            Coupon coupon = base().discountType(DiscountType.RATE).discountValue(10).maxDiscountAmount(3_000L).build();

            // when
            long discount = coupon.calculateDiscount(50_000, 3_000);

            // then — 50,000의 10% = 5,000이지만 상한 3,000으로 캡핑
            assertThat(discount).isEqualTo(3_000);
        }

        @Test
        void AMOUNT는_정액만큼_할인하되_주문총액을_넘지않는다() {
            // given
            Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(100_000).build();

            // when
            long discount = coupon.calculateDiscount(10_000, 3_000);

            // then — 정액 100,000이어도 총액(13,000)을 넘을 수 없다
            assertThat(discount).isEqualTo(13_000);
        }

        @Test
        void FREE_SHIPPING은_배송비만큼만_할인한다() {
            // given
            Coupon coupon = base().discountType(DiscountType.FREE_SHIPPING).discountValue(0).build();

            // when
            long discount = coupon.calculateDiscount(50_000, 3_000);

            // then
            assertThat(discount).isEqualTo(3_000);
        }
    }

    @Test
    void 만료시각이_지나면_isExpired가_true다() {
        // given
        Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000)
                .expiresAt(Instant.now().minusSeconds(1)).build();

        // when & then
        assertThat(coupon.isExpired(Instant.now())).isTrue();
    }

    @Test
    void 최소_펀딩금액_미달이면_meetsMinFundingAmount가_false다() {
        // given
        Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000).minFundingAmount(30_000).build();

        // when & then
        assertThat(coupon.meetsMinFundingAmount(20_000)).isFalse();
        assertThat(coupon.meetsMinFundingAmount(30_000)).isTrue();
    }

    @Nested
    class 대상_매칭 {

        @Test
        void ALL이면_어떤_프로젝트든_매칭된다() {
            Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000)
                    .targetScope(CouponTargetScope.ALL).build();
            assertThat(coupon.matchesProject(999L)).isTrue();
        }

        @Test
        void PROJECT면_targetRefId가_같을때만_매칭된다() {
            Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000)
                    .targetScope(CouponTargetScope.PROJECT).targetRefId("10").build();
            assertThat(coupon.matchesProject(10L)).isTrue();
            assertThat(coupon.matchesProject(11L)).isFalse();
        }
    }

    @Nested
    class 예산_한도 {

        @Test
        void budgetLimit이_없으면_항상_여유가_있다() {
            Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000).budgetLimit(null).build();
            assertThat(coupon.hasRemainingBudget(1_000_000)).isTrue();
        }

        @Test
        void 누적사용액과_예상차감액의_합이_한도를_넘으면_false다() {
            Coupon coupon = base().discountType(DiscountType.AMOUNT).discountValue(1000)
                    .budgetLimit(10_000L).usedBudgetAmount(8_000L).build();
            assertThat(coupon.hasRemainingBudget(1_000)).isTrue();
            assertThat(coupon.hasRemainingBudget(3_000)).isFalse();
        }
    }
}
