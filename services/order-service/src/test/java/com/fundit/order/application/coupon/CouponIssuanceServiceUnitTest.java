package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponIssuanceServiceUnitTest {

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
    void 클레임하면_발급되고_수량이_차감된다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(Optional.of(coupon()));
        when(couponIssuanceRepository.countByCouponCodeAndOwnerId("LIVE-XY12", memberId)).thenReturn(0L);
        when(couponRepository.decreaseRemainingQuantity("LIVE-XY12")).thenReturn(true);
        when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        CouponIssuance issuance = couponIssuanceService.claim(memberId, "LIVE-XY12");

        // then
        assertThat(issuance.isAvailable()).isTrue();
        assertThat(issuance.getOwnerId()).isEqualTo(memberId);
    }

    @Test
    void 자동발급하면_발급되고_수량이_차감된다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("WELCOME")).thenReturn(Optional.of(coupon().toBuilder().couponCode("WELCOME").build()));
        when(couponIssuanceRepository.countByCouponCodeAndOwnerId("WELCOME", memberId)).thenReturn(0L);
        when(couponRepository.decreaseRemainingQuantity("WELCOME")).thenReturn(true);
        when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Optional<CouponIssuance> issuance = couponIssuanceService.autoIssue(memberId, "WELCOME");

        // then
        assertThat(issuance).isPresent();
    }

    @Test
    void 자동발급_대상_쿠폰이_없으면_빈값을_반환한다() {
        // given
        when(couponRepository.findByCouponCode("MISSING")).thenReturn(Optional.empty());

        // when
        Optional<CouponIssuance> issuance = couponIssuanceService.autoIssue(UUID.randomUUID(), "MISSING");

        // then
        assertThat(issuance).isEmpty();
    }

    @Test
    void 자동발급시_만료된_쿠폰이면_빈값을_반환한다() {
        // given
        Coupon expired = coupon().toBuilder().expiresAt(Instant.now().minusSeconds(1)).build();
        when(couponRepository.findByCouponCode("EXPIRED")).thenReturn(Optional.of(expired));

        // when
        Optional<CouponIssuance> issuance = couponIssuanceService.autoIssue(UUID.randomUUID(), "EXPIRED");

        // then
        assertThat(issuance).isEmpty();
    }

    @Test
    void 자동발급시_소진됐으면_예외없이_빈값을_반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(couponRepository.findByCouponCode("SOLDOUT")).thenReturn(Optional.of(coupon().toBuilder().couponCode("SOLDOUT").build()));
        when(couponIssuanceRepository.countByCouponCodeAndOwnerId("SOLDOUT", memberId)).thenReturn(0L);
        when(couponRepository.decreaseRemainingQuantity("SOLDOUT")).thenReturn(false);

        // when
        Optional<CouponIssuance> issuance = couponIssuanceService.autoIssue(memberId, "SOLDOUT");

        // then
        assertThat(issuance).isEmpty();
    }
}
