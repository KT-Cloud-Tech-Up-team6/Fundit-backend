package com.fundit.order.presentation.dto;

import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.order.OrderQueryService.OrderListItem;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ORDER-004 목록 응답 — projectId는 project-service의 publicId(UUID)로 채워진다(cross-service ID 통일 #69).
 * {@code sellerDisplayName}/{@code thumbnailUrl}은 project-service 조회 실패 시 null일 수 있다(V03, 부가 정보).
 */
public record OrderSummaryResponse(
        UUID orderId, UUID projectId, String projectTitle, String status, long discountAmount, long finalAmount,
        Instant createdAt, String sellerDisplayName, String thumbnailUrl, String rewardSummary, int totalQuantity,
        List<String> availableActions
) {

    public static OrderSummaryResponse from(OrderListItem item) {
        Funding funding = item.funding();
        ProjectSummaryClient.ProjectSummary summary = item.projectSummary();
        List<FundingLineItem> lineItems = funding.getLineItems();
        long finalAmount = funding.totalRewardAmount() + funding.getShippingFee() - item.discountAmount();
        return new OrderSummaryResponse(funding.getPublicId(), funding.getProjectId(), funding.getProjectTitle(),
                funding.getStatus().name(), item.discountAmount(), finalAmount, funding.getCreatedAt(),
                summary == null ? null : summary.sellerDisplayName(), summary == null ? null : summary.thumbnailUrl(),
                rewardSummary(lineItems), lineItems.stream().mapToInt(FundingLineItem::quantity).sum(),
                item.availableActions());
    }

    /** FundingInternalQueryService.orderName()과 동일한 표시 규칙(리워드명 외 N건). */
    private static String rewardSummary(List<FundingLineItem> lineItems) {
        if (lineItems.isEmpty()) {
            return "";
        }
        String firstRewardName = lineItems.get(0).rewardName();
        return lineItems.size() == 1 ? firstRewardName : firstRewardName + " 외 " + (lineItems.size() - 1) + "건";
    }
}
