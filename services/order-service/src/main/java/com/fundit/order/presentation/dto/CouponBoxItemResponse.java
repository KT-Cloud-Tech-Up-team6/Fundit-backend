package com.fundit.order.presentation.dto;

import com.fundit.order.application.coupon.CouponBoxQueryService;

import java.time.Instant;

public record CouponBoxItemResponse(
        String couponCode, String couponName, String discountType, long discountValue, String status, Instant expiresAt
) {

    public static CouponBoxItemResponse from(CouponBoxQueryService.CouponBoxItem item) {
        var coupon = item.coupon();
        var issuance = item.issuance();
        return new CouponBoxItemResponse(
                issuance.getCouponCode(),
                coupon == null ? null : coupon.getCouponName(),
                coupon == null ? null : coupon.getDiscountType().name(),
                coupon == null ? 0 : coupon.getDiscountValue(),
                issuance.getStatus().name(),
                coupon == null ? null : coupon.getExpiresAt());
    }
}
