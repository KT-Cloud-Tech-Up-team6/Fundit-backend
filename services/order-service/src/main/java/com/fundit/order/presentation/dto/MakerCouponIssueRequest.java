package com.fundit.order.presentation.dto;

import com.fundit.order.application.coupon.MakerCouponIssueCommand;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.DropType;
import com.fundit.order.domain.coupon.IssueChannel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/**
 * projectId는 아직 레거시 Long(v1) 계약 — 컨트롤러가 project-service에서 UUID를 먼저 해석한다.
 *
 * <p>{@code issueChannel}을 생략하면 GENERAL이다(기존 클라이언트 호환). {@code liveId}는 방송의
 * 공개 UUID로, 컨트롤러가 live-service에서 내부 세션 PK로 해석한다 — 판매자 FE도 BIGINT는 모른다.
 */
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
        @NotNull @Future Instant expiresAt,
        IssueChannel issueChannel,
        UUID liveId,
        DropType dropType
) {

    public IssueChannel resolveIssueChannel() {
        return issueChannel == null ? IssueChannel.GENERAL : issueChannel;
    }

    /** LIVE 쿠폰인데 liveId가 없으면 어느 방송에 매달지 정할 수 없다 — 500이 아니라 400으로 돌려보낸다. */
    @AssertTrue(message = "LIVE 쿠폰은 liveId가 필요합니다.")
    public boolean isLiveIdPresentForLiveChannel() {
        return resolveIssueChannel() != IssueChannel.LIVE || liveId != null;
    }

    public MakerCouponIssueCommand toCommand(UUID resolvedProjectId, Long liveSessionId, UUID liveSellerId) {
        return new MakerCouponIssueCommand(resolvedProjectId, couponName, discountType, discountValue,
                maxDiscountAmount, budgetLimit, quantity, minFundingAmount, perMemberLimit, expiresAt,
                resolveIssueChannel(), dropType, liveSessionId, liveSellerId);
    }
}
