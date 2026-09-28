package com.fundit.order.application.order;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceUnitTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long REWARD_ID = 1L;
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    private RewardCatalogClient rewardCatalogClient;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;
    @Mock
    private ProjectSummaryClient projectSummaryClient;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    private OrderPricingService service;

    @BeforeEach
    void setUp() {
        service = new OrderPricingService(rewardCatalogClient, couponRepository, couponIssuanceRepository,
                projectSummaryClient, projectOwnershipClient, 3_000L);
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
                List.of(new OrderLineItemRequest(REWARD_ID, 2, null)), null, false);

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
                List.of(new OrderLineItemRequest(REWARD_ID, 1, List.of(100L))), null, false);

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
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("WELCOME"), false);

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
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("OTHER"), false);

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
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("GHOST"), false);

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
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("MIN30K"), false);

            // then
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("MIN30K", "MIN_AMOUNT_NOT_MET"));
        }

        @Test
        void 예산이_소진된_쿠폰은_BUDGET_EXCEEDED로_분류된다() {
            // given — 할인액 2,000인데 이미 사용된 예산이 9,000이고 한도가 10,000이라 여유가 1,000뿐
            Coupon coupon = couponBase("BUDGETOUT", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(2_000).budgetLimit(10_000L).usedBudgetAmount(9_000L).build();
            CouponIssuance issuance = CouponIssuance.issue("BUDGETOUT", MEMBER_ID);
            when(couponRepository.findByCouponCode("BUDGETOUT")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("BUDGETOUT", MEMBER_ID))
                    .thenReturn(Optional.of(issuance));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("BUDGETOUT"), false);

            // then
            assertThat(result.appliedCoupons()).isEmpty();
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("BUDGETOUT", "BUDGET_EXCEEDED"));
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
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("PLAT", "MAKER"), false);

            // then
            assertThat(result.discountAmount()).isEqualTo(1_500L);
            assertThat(result.appliedCoupons()).hasSize(2);
        }

        /** 주문 금액 = 리워드 10,000 + 배송비 3,000 = 13,000. */
        private OrderPricingService.PricingResult calculateWith(long platformDiscount, long makerDiscount) {
            // given
            Coupon platform = couponBase("PLAT", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue((int) platformDiscount).build();
            Coupon maker = couponBase("MAKER", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue((int) makerDiscount).build();
            when(couponRepository.findByCouponCode("PLAT")).thenReturn(Optional.of(platform));
            when(couponRepository.findByCouponCode("MAKER")).thenReturn(Optional.of(maker));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("PLAT", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("PLAT", MEMBER_ID)));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("MAKER", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("MAKER", MEMBER_ID)));
            return service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("PLAT", "MAKER"), false);
        }

        @Test
        void 할인_합계가_주문_금액과_같으면_결제_금액이_0원이라_작은_쿠폰을_거절한다() {
            // when
            OrderPricingService.PricingResult result = calculateWith(10_000L, 3_000L);

            // then — 큰 할인부터 채우고, 결제 금액을 0원으로 만드는 쿠폰은 사용 불가 사유와 함께 내려간다
            assertThat(result.appliedCoupons()).extracting(OrderPricingService.AppliedCoupon::couponCode)
                    .containsExactly("PLAT");
            assertThat(result.finalAmount()).isEqualTo(3_000L);
            assertThat(result.unavailableCoupons()).containsExactly(
                    new OrderPricingService.UnavailableCoupon("MAKER", "EXCEEDS_ORDER_AMOUNT"));
        }

        @Test
        void 할인_합계가_주문_금액을_넘어도_거절한다() {
            // when
            OrderPricingService.PricingResult result = calculateWith(10_000L, 5_000L);

            // then
            assertThat(result.unavailableCoupons()).extracting(OrderPricingService.UnavailableCoupon::reason)
                    .containsExactly("EXCEEDS_ORDER_AMOUNT");
            assertThat(result.finalAmount()).isPositive();
        }

        @Test
        void 결제_금액이_1원이라도_남으면_두_쿠폰을_모두_적용한다() {
            // when
            OrderPricingService.PricingResult result = calculateWith(10_000L, 2_999L);

            // then
            assertThat(result.appliedCoupons()).hasSize(2);
            assertThat(result.finalAmount()).isEqualTo(1L);
            assertThat(result.unavailableCoupons()).isEmpty();
        }
    }

    @Nested
    class 스코프_매칭 {

        @Test
        void CATEGORY_스코프는_프로젝트_카테고리가_일치해야_적용된다() {
            // given
            Coupon coupon = couponBase("FASHION10", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).targetScope(CouponTargetScope.CATEGORY).targetRefId("패션").build();
            when(couponRepository.findByCouponCode("FASHION10")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("FASHION10", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("FASHION10", MEMBER_ID)));
            when(projectSummaryClient.getCategoryMajor(PROJECT_ID)).thenReturn(Optional.of("패션"));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("FASHION10"), false);

            // then
            assertThat(result.appliedCoupons()).hasSize(1);
        }

        @Test
        void CATEGORY_스코프는_카테고리가_다르면_NOT_APPLICABLE로_분류된다() {
            // given
            Coupon coupon = couponBase("FASHION10", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).targetScope(CouponTargetScope.CATEGORY).targetRefId("패션").build();
            when(couponRepository.findByCouponCode("FASHION10")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("FASHION10", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("FASHION10", MEMBER_ID)));
            when(projectSummaryClient.getCategoryMajor(PROJECT_ID)).thenReturn(Optional.of("가전"));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("FASHION10"), false);

            // then
            assertThat(result.unavailableCoupons()).singleElement()
                    .isEqualTo(new OrderPricingService.UnavailableCoupon("FASHION10", "NOT_APPLICABLE"));
        }

        @Test
        void MAKER_스코프는_프로젝트_판매자가_일치해야_적용된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            Coupon coupon = couponBase("SELLERVIP", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).targetScope(CouponTargetScope.MAKER).targetRefId(sellerId.toString()).build();
            when(couponRepository.findByCouponCode("SELLERVIP")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("SELLERVIP", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("SELLERVIP", MEMBER_ID)));
            when(projectOwnershipClient.findSellerId(PROJECT_ID)).thenReturn(Optional.of(sellerId));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("SELLERVIP"), false);

            // then
            assertThat(result.appliedCoupons()).hasSize(1);
        }

        @Test
        void ALL_스코프_쿠폰만_있으면_project_service를_조회하지_않는다() {
            // given
            Coupon coupon = couponBase("WELCOME", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_000).build();
            when(couponRepository.findByCouponCode("WELCOME")).thenReturn(Optional.of(coupon));
            when(couponIssuanceRepository.findByCouponCodeAndOwnerId("WELCOME", MEMBER_ID))
                    .thenReturn(Optional.of(CouponIssuance.issue("WELCOME", MEMBER_ID)));

            // when
            service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("WELCOME"), false);

            // then
            org.mockito.Mockito.verifyNoInteractions(projectSummaryClient, projectOwnershipClient);
        }
    }

    @Nested
    class 최적_쿠폰_자동추천 {

        @Test
        void 보유쿠폰중_발급주체별로_할인액이_가장_큰_것만_자동_적용한다() {
            // given
            Coupon platformLow = couponBase("PLAT-LOW", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(500).build();
            Coupon platformHigh = couponBase("PLAT-HIGH", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(1_500).build();
            Coupon maker = couponBase("MAKER", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(700).build();
            when(couponIssuanceRepository.findByOwnerId(MEMBER_ID, CouponIssuanceStatus.AVAILABLE, Pageable.unpaged()))
                    .thenReturn(new PageImpl<>(List.of(
                            CouponIssuance.issue("PLAT-LOW", MEMBER_ID),
                            CouponIssuance.issue("PLAT-HIGH", MEMBER_ID),
                            CouponIssuance.issue("MAKER", MEMBER_ID))));
            when(couponRepository.findByCouponCodeIn(any()))
                    .thenReturn(List.of(platformLow, platformHigh, maker));

            // when — couponCodes를 넘겨도 autoApplyBestCoupon=true면 무시된다
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("PLAT-LOW"), true);

            // then
            assertThat(result.appliedCoupons()).hasSize(2)
                    .extracting(OrderPricingService.AppliedCoupon::couponCode)
                    .containsExactlyInAnyOrder("PLAT-HIGH", "MAKER");
            assertThat(result.discountAmount()).isEqualTo(2_200L);
            assertThat(result.unavailableCoupons()).isEmpty();
        }

        @Test
        void 최대_할인_쿠폰이_주문_금액을_넘으면_같은_발급주체의_차선_쿠폰을_고른다() {
            // given — 주문 금액 13,000. PLAT-ALL(13,000)은 결제 금액을 0원으로 만든다
            Coupon platformAll = couponBase("PLAT-ALL", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(13_000).build();
            Coupon platformMid = couponBase("PLAT-MID", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(5_000).build();
            Coupon maker = couponBase("MAKER", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(700).build();
            when(couponIssuanceRepository.findByOwnerId(MEMBER_ID, CouponIssuanceStatus.AVAILABLE, Pageable.unpaged()))
                    .thenReturn(new PageImpl<>(List.of(
                            CouponIssuance.issue("PLAT-ALL", MEMBER_ID),
                            CouponIssuance.issue("PLAT-MID", MEMBER_ID),
                            CouponIssuance.issue("MAKER", MEMBER_ID))));
            when(couponRepository.findByCouponCodeIn(any())).thenReturn(List.of(platformAll, platformMid, maker));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), null, true);

            // then
            assertThat(result.appliedCoupons()).extracting(OrderPricingService.AppliedCoupon::couponCode)
                    .containsExactlyInAnyOrder("PLAT-MID", "MAKER");
            assertThat(result.finalAmount()).isEqualTo(7_300L);
        }

        @Test
        void 결제_금액이_남는_조합_중_할인_합계가_가장_큰_조합을_고른다() {
            // given — 주문 금액 13,000. 9,000+5,000은 넘치고 9,000+3,500(12,500)이 최대다
            Coupon platformHigh = couponBase("PLAT-9000", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(9_000).build();
            Coupon platformLow = couponBase("PLAT-3000", IssuerType.PLATFORM).discountType(DiscountType.AMOUNT)
                    .discountValue(3_000).build();
            Coupon makerHigh = couponBase("MAKER-5000", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(5_000).build();
            Coupon makerLow = couponBase("MAKER-3500", IssuerType.MAKER).discountType(DiscountType.AMOUNT)
                    .discountValue(3_500).build();
            when(couponIssuanceRepository.findByOwnerId(MEMBER_ID, CouponIssuanceStatus.AVAILABLE, Pageable.unpaged()))
                    .thenReturn(new PageImpl<>(List.of(
                            CouponIssuance.issue("PLAT-9000", MEMBER_ID),
                            CouponIssuance.issue("PLAT-3000", MEMBER_ID),
                            CouponIssuance.issue("MAKER-5000", MEMBER_ID),
                            CouponIssuance.issue("MAKER-3500", MEMBER_ID))));
            when(couponRepository.findByCouponCodeIn(any()))
                    .thenReturn(List.of(platformHigh, platformLow, makerHigh, makerLow));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), null, true);

            // then
            assertThat(result.appliedCoupons()).extracting(OrderPricingService.AppliedCoupon::couponCode)
                    .containsExactlyInAnyOrder("PLAT-9000", "MAKER-3500");
            assertThat(result.finalAmount()).isEqualTo(500L);
        }

        @Test
        void 보유쿠폰이_없으면_할인없이_계산된다() {
            // given
            when(couponIssuanceRepository.findByOwnerId(MEMBER_ID, CouponIssuanceStatus.AVAILABLE, Pageable.unpaged()))
                    .thenReturn(new PageImpl<>(List.of()));

            // when
            OrderPricingService.PricingResult result = service.calculate(MEMBER_ID, PROJECT_ID,
                    List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), null, true);

            // then
            assertThat(result.appliedCoupons()).isEmpty();
            assertThat(result.discountAmount()).isZero();
        }
    }
}
