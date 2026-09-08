package com.fundit.order.presentation.dto;

import com.fundit.order.domain.coupon.Coupon;

public record MakerCouponIssueResponse(
        String couponCode, String couponName, int remainingQuantity, Long budgetLimit, long usedBudgetAmount
) {

    public static MakerCouponIssueResponse from(Coupon coupon) {
        return new MakerCouponIssueResponse(coupon.getCouponCode(), coupon.getCouponName(),
                coupon.getRemainingQuantity(), coupon.getBudgetLimit(), coupon.getUsedBudgetAmount());
    }
}
