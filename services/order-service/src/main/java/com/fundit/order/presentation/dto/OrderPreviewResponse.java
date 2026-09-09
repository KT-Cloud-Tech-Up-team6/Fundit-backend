package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderPricingService;

import java.util.List;

public record OrderPreviewResponse(
        long rewardAmount,
        long shippingFee,
        long discountAmount,
        long finalAmount,
        List<AppliedCouponResponse> appliedCoupons,
        List<UnavailableCouponResponse> unavailableCoupons
) {

    public static OrderPreviewResponse from(OrderPricingService.PricingResult result) {
        return new OrderPreviewResponse(
                result.rewardAmount(), result.shippingFee(), result.discountAmount(), result.finalAmount(),
                result.appliedCoupons().stream().map(AppliedCouponResponse::from).toList(),
                result.unavailableCoupons().stream().map(UnavailableCouponResponse::from).toList());
    }
}
