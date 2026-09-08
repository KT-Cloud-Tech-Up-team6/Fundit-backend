package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponIssuanceServiceUnitExceptionTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;

    @InjectMocks
    private CouponIssuanceService couponIssuanceService;

    private Coupon coupon() {
        return Coupon.builder().id(1L).couponCode("LIVE-XY12").couponName("쿠폰")
                .discountType(DiscountType.AMOUNT).discountValue(3_000)
                .issuerType(IssuerType.PLATFORM).targetScope(CouponTargetScope.ALL)
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(5)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.LIVE).version(0).build();
    }

    @Test
    void 존재하지_않는_쿠폰코드면_NOT_FOUND_예외가_발생한다() {
        // given
        when(couponRepository.findByCouponCode("GHOST")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.claim(UUID.randomUUID(), "GHOST"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 만료된_쿠폰이면_COUPON_NOT_APPLICABLE_예외가_발생한다() {
        // given
        Coupon expired = coupon().toBuilder().expiresAt(Instant.now().minusSeconds(1)).build();
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(expired));

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.claim(UUID.randomUUID(), "LIVE-XY12"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_NOT_APPLICABLE));
    }

    @Test
    void 일인_발급한도를_초과하면_COUPON_NOT_APPLICABLE_예외가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(couponIssuanceRepository.countByCouponCodeAndOwnerId("LIVE-XY12", memberId)).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.claim(memberId, "LIVE-XY12"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_NOT_APPLICABLE));
    }

    @Test
    void 쿠폰이_소진됐으면_COUPON_EXHAUSTED_예외가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(couponIssuanceRepository.countByCouponCodeAndOwnerId("LIVE-XY12", memberId)).thenReturn(0L);
        when(couponRepository.decreaseRemainingQuantity("LIVE-XY12")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> couponIssuanceService.claim(memberId, "LIVE-XY12"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_EXHAUSTED));
    }
}
