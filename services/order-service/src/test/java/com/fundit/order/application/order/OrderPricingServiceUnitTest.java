package com.fundit.order.application.order;

import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceUnitTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long REWARD_ID = 1L;
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    private RewardCatalogClient rewardCatalogClient;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;

    private OrderPricingService service;

    @BeforeEach
    void setUp() {
        service = new OrderPricingService(rewardCatalogClient, couponRepository, couponIssuanceRepository, 3_000L);
        lenient().when(rewardCatalogClient.getRewards(PROJECT_ID)).thenReturn(List.of(rewardSnapshot()));
    }

    private RewardCatalogClient.RewardSnapshot rewardSnapshot() {
        var optionGroup = new RewardCatalogClient.OptionGroupSnapshot(10L, "색상",
                List.of(new RewardCatalogClient.OptionValueSnapshot(100L, "화이트")));
        return new RewardCatalogClient.RewardSnapshot(REWARD_ID, "얼리버드 패키지", 10_000L, true, List.of(optionGroup));
    }

    private Coupon.CouponBuilder couponBase(String code, IssuerType issuerType) {
        return Coupon.builder().id(1L).couponCode(code).couponName("쿠폰").issuerType(issuerType)
                .targetScope(CouponTargetScope.ALL).minFundingAmount(0).perMemberLimit(1).remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.GENERAL).version(0);
    }

    @Test
    void 쿠폰없이_리워드금액과_배송비를_계산한다() {
        // when
        OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 2, null)), null);

        // then
        assertThat(result.rewardAmount()).isEqualTo(20_000L);
        assertThat(result.shippingFee()).isEqualTo(3_000L);
        assertThat(result.discountAmount()).isZero();
        assertThat(result.finalAmount()).isEqualTo(23_000L);
    }

    @Test
    void 옵션값을_스냅샷으로_해석한다() {
        // when
        OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 1, List.of(100L))), null);

        // then
        assertThat(result.lineItems()).singleElement()
                .satisfies(li -> assertThat(li.options()).containsExactly(
                        new OrderPricingService.ResolvedOption(10L, "색상", 100L, "화이트")));
    }

    @Nested
    class 쿠폰_적용 {

        @Test
        void 유효한_쿠폰이면_할인이_적용된다() {
            // given
            Coupon coupon = couponBase("WELCOME", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(2_000).build();
            CouponIssuance issuance = CouponIssuance.issue("WELCOME", MEMBER_ID).toBuilder().id(5L).build();
            when(couponRepository.findByCouponCode("WELCOME")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("WELCOME", MEMBER_ID))
                    .thenReturn(Optional.of(issuance));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("WELCOME"));

            // then
            assertThat(result.discountAmount()).isEqualTo(2_000L);
            assertThat(result.appliedCoupons()).singleElement()
                    .extracting(OrderPricingService.AppliedCoupon::couponIssuanceId).isEqualTo(5L);
            assertThat(result.unavailableCoupons()).isEmpty();
        }

        @Test
        void 보유하지_않은_쿠폰은_NOT_OWNED로_분류된다() {
            // given
            Coupon coupon = couponBase("OTHER", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).build();
            when(couponRepository.findByCouponCode("OTHER")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("OTHER", MEMBER_ID)).thenReturn(Optional.empty());

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("OTHER"));

            // then
            assertThat(result.appliedCoupons()).isEmpty();
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("OTHER", "NOT_OWNED"));
        }

        @Test
        void 존재하지_않는_쿠폰코드는_NOT_FOUND로_분류된다() {
            // given
            when(couponRepository.findByCouponCode("GHOST")).thenReturn(Optional.empty());

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("GHOST"));

            // then
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("GHOST", "NOT_FOUND"));
        }

        @Test
        void 최소금액_미달_쿠폰은_MIN_AMOUNT_NOT_MET으로_분류된다() {
            // given — 리워드금액 10,000인데 최소 펀딩금액 30,000
            Coupon coupon = couponBase("MIN30K", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).minFundingAmount(30_000).build();
            CouponIssuance issuance = CouponIssuance.issue("MIN30K", MEMBER_ID);
            when(couponRepository.findByCouponCode("MIN30K")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("MIN30K", MEMBER_ID))
                    .thenReturn(Optional.of(issuance));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("MIN30K"));

            // then
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("MIN30K", "MIN_AMOUNT_NOT_MET"));
        }

        @Test
        void 플랫폼쿠폰과_메이커쿠폰을_각각_하나씩_적용할_수_있다() {
            // given
            Coupon platform = couponBase("PLAT", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).build();
            Coupon maker = couponBase("MAKER", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(500).build();
            when(couponRepository.findByCouponCode("PLAT")).thenReturn(Optional.of(platform));
            when(couponRepository.findByCouponCode("MAKER")).thenReturn(Optional.of(maker));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("PLAT", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("PLAT", MEMBER_ID)));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("MAKER", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("MAKER", MEMBER_ID)));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("PLAT", "MAKER"));

            // then
            assertThat(result.discountAmount()).isEqualTo(1_500L);
            assertThat(result.appliedCoupons()).hasSize(2);
        }
    }
}
