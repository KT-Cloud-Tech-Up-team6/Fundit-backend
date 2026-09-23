package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.Coupon;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponClaimServiceUnitTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private LiveStatusClient liveStatusClient;
    @Mock
    private CouponIssuanceService couponIssuanceService;

    @InjectMocks
    private CouponClaimService couponClaimService;

    private static final long LIVE_SESSION_ID = 42L;

    private Coupon coupon() {
        return Coupon.builder().id(1L).couponCode("LIVE-XY12").couponName("쿠폰")
                .discountType(DiscountType.AMOUNT).discountValue(3_000)
                .issuerType(IssuerType.PLATFORM).targetScope(CouponTargetScope.ALL)
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(5)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.LIVE)
                .liveSessionId(LIVE_SESSION_ID).version(0).build();
    }

    @Test
    void 방송중이면_발급_트랜잭션으로_넘긴다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(liveStatusClient.findBySessionId(LIVE_SESSION_ID)).thenReturn(
                Optional.of(new LiveStatusClient.LiveStatus(UUID.randomUUID(), LIVE_SESSION_ID, "LIVE", UUID.randomUUID())));

        // when
        couponClaimService.claim(memberId, "LIVE-XY12");

        // then
        verify(couponIssuanceService).claim(memberId, "LIVE-XY12");
    }

    @Test
    void GENERAL_쿠폰은_live를_호출하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        Coupon general = coupon().toBuilder().couponCode("GEN-1").issueChannel(IssueChannel.GENERAL)
                .liveSessionId(null).build();
        when(couponRepository.findByCouponCode("GEN-1")).thenReturn(Optional.of(general));

        // when
        couponClaimService.claim(memberId, "GEN-1");

        // then — 방송 여부를 따질 이유가 없는 쿠폰이라 live-service를 아예 부르지 않는다
        verifyNoInteractions(liveStatusClient);
        verify(couponIssuanceService).claim(memberId, "GEN-1");
    }

    @Test
    void 존재하지_않는_쿠폰코드면_NOT_FOUND_예외가_발생한다() {
        // given
        when(couponRepository.findByCouponCode("GHOST")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponClaimService.claim(UUID.randomUUID(), "GHOST"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 방송중이_아니면_COUPON_NOT_APPLICABLE_예외가_발생한다() {
        // given
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(liveStatusClient.findBySessionId(LIVE_SESSION_ID)).thenReturn(
                Optional.of(new LiveStatusClient.LiveStatus(UUID.randomUUID(), LIVE_SESSION_ID, "ENDED", UUID.randomUUID())));

        // when & then
        assertThatThrownBy(() -> couponClaimService.claim(UUID.randomUUID(), "LIVE-XY12"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_NOT_APPLICABLE));
        verifyNoInteractions(couponIssuanceService);
    }

    @Test
    void 방송_세션이_없으면_COUPON_NOT_APPLICABLE_예외가_발생한다() {
        // given — order가 live에 없는 세션을 참조 중이라는 뜻(정합성 경고 대상)이라도 발급은 막는다
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(liveStatusClient.findBySessionId(LIVE_SESSION_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponClaimService.claim(UUID.randomUUID(), "LIVE-XY12"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_NOT_APPLICABLE));
        verifyNoInteractions(couponIssuanceService);
    }

    @Test
    void live_조회에_실패하면_발급하지_않고_예외를_전파한다() {
        // given — 쿠폰은 게이트라 못 믿으면 닫는다(503). 주문의 라이브ID(꼬리표)와 반대 정책이다.
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(liveStatusClient.findBySessionId(LIVE_SESSION_ID))
                .thenThrow(new DependencyFailureException(new IllegalStateException("timeout")));

        // when & then
        assertThatThrownBy(() -> couponClaimService.claim(UUID.randomUUID(), "LIVE-XY12"))
                .isInstanceOf(DependencyFailureException.class);
        verifyNoInteractions(couponIssuanceService);
    }
}
