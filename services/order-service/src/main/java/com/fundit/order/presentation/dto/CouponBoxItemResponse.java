package com.fundit.order.presentation.dto;

import com.fundit.order.application.coupon.CouponBoxQueryService;

import java.time.Instant;

/** ORDER-009 — 쿠폰함 조회. PM 정책 16.2.4(사용 조건·적용 대상·유효기간 표시)를 만족하도록 필드를 채운다. */
public record CouponBoxItemResponse(
        String couponCode, String couponName, String discountType, long discountValue, String status,
        Instant expiresAt, long minFundingAmount, int perMemberLimit, String targetScope, String targetRefId,
        String issuerType, Long maxDiscountAmount
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
                coupon == null ? null : coupon.getExpiresAt(),
                coupon == null ? 0 : coupon.getMinFundingAmount(),
                coupon == null ? 0 : coupon.getPerMemberLimit(),
                coupon == null ? null : coupon.getTargetScope().name(),
                coupon == null ? null : coupon.getTargetRefId(),
                coupon == null ? null : coupon.getIssuerType().name(),
                coupon == null ? null : coupon.getMaxDiscountAmount());
    }
}
