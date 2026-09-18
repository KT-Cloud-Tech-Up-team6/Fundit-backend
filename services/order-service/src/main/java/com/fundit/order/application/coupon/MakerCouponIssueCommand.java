package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.DiscountType;

import java.time.Instant;
import java.util.UUID;

public record MakerCouponIssueCommand(
        UUID projectId,
        String couponName,
        DiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        Long budgetLimit,
        int quantity,
        long minFundingAmount,
        int perMemberLimit,
        Instant expiresAt
) {
}
