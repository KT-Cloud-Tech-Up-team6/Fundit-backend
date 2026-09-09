package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceUnitExceptionTest {

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
        var optionGroup = new RewardCatalogClient.OptionGroupSnapshot(10L, "색상",
                List.of(new RewardCatalogClient.OptionValueSnapshot(100L, "화이트")));
        lenient().when(rewardCatalogClient.getRewards(PROJECT_ID)).thenReturn(
                List.of(new RewardCatalogClient.RewardSnapshot(REWARD_ID, "리워드", 10_000L, true, List.of(optionGroup))));
    }

    @Test
    void 쿠폰이_3개_이상이면_예외가_발생한다() {
        assertThatThrownBy(() -> service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("A", "B", "C")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    void 같은_issuer_type_쿠폰_2개를_동시에_적용하면_예외가_발생한다() {
        // given
        Coupon platform1 = coupon("P1", IssuerType.PLATFORM);
        Coupon platform2 = coupon("P2", IssuerType.PLATFORM);
        when(couponRepository.findByCouponCode("P1")).thenReturn(Optional.of(platform1));
        when(couponRepository.findByCouponCode("P2")).thenReturn(Optional.of(platform2));

        // when & then
        assertThatThrownBy(() -> service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 1, null)), List.of("P1", "P2")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    void 존재하지_않는_리워드면_예외가_발생한다() {
        assertThatThrownBy(() -> service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(999L, 1, null)), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 존재하지_않는_옵션이면_예외가_발생한다() {
        assertThatThrownBy(() -> service.calculate(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 1, List.of(999L))), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private Coupon coupon(String code, IssuerType issuerType) {
        return Coupon.builder().id(1L).couponCode(code).couponName("쿠폰").issuerType(issuerType)
                .discountType(com.fundit.order.domain.coupon.DiscountType.AMOUNT).discountValue(1000)
                .targetScope(CouponTargetScope.ALL).minFundingAmount(0).perMemberLimit(1).remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.GENERAL).version(0).build();
    }
}
