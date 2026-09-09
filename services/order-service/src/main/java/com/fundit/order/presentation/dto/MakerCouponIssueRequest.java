package com.fundit.order.presentation.dto;

import com.fundit.order.application.coupon.MakerCouponIssueCommand;
import com.fundit.order.domain.coupon.DiscountType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record MakerCouponIssueRequest(
        @NotNull Long projectId,
        @NotBlank String couponName,
        @NotNull DiscountType discountType,
        @PositiveOrZero long discountValue,
        Long maxDiscountAmount,
        Long budgetLimit,
        @Min(1) int quantity,
        @PositiveOrZero long minFundingAmount,
        @Min(1) int perMemberLimit,
        @NotNull @Future Instant expiresAt
) {

    public MakerCouponIssueCommand toCommand() {
        return new MakerCouponIssueCommand(projectId, couponName, discountType, discountValue, maxDiscountAmount,
                budgetLimit, quantity, minFundingAmount, perMemberLimit, expiresAt);
    }
}
