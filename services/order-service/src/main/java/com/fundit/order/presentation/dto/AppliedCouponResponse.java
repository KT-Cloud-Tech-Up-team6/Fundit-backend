package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderPricingService;

public record AppliedCouponResponse(String couponCode, String issuerType, String discountType) {

    public static AppliedCouponResponse from(OrderPricingService.AppliedCoupon applied) {
        return new AppliedCouponResponse(applied.couponCode(), applied.issuerType().name(), applied.discountType().name());
    }
}
