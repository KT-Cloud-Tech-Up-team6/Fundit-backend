package com.fundit.order.presentation.dto;

import com.fundit.order.application.coupon.MakerCouponIssueCommand;
import com.fundit.order.domain.coupon.DiscountType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/** projectId는 아직 레거시 Long(v1) 계약 — 컨트롤러가 project-service에서 UUID를 먼저 해석한다. */
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

    public MakerCouponIssueCommand toCommand(UUID resolvedProjectId) {
        return new MakerCouponIssueCommand(resolvedProjectId, couponName, discountType, discountValue,
                maxDiscountAmount, budgetLimit, quantity, minFundingAmount, perMemberLimit, expiresAt);
    }
}
