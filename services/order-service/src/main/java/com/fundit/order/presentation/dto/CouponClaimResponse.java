package com.fundit.order.presentation.dto;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;

public record CouponClaimResponse(String couponCode, boolean issued, java.time.Instant expiresAt) {

    public static CouponClaimResponse from(CouponIssuance issuance, Coupon coupon) {
        return new CouponClaimResponse(issuance.getCouponCode(), true, coupon.getExpiresAt());
    }
}
