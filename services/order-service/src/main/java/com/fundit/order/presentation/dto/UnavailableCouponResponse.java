package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderPricingService;

public record UnavailableCouponResponse(String couponCode, String reason) {

    public static UnavailableCouponResponse from(OrderPricingService.UnavailableCoupon unavailable) {
        return new UnavailableCouponResponse(unavailable.couponCode(), unavailable.reason());
    }
}
