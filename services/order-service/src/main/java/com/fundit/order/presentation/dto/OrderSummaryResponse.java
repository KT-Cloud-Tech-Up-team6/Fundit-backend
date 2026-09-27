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
 *
 * <p>{@code progressStage}는 화면 배지용 파생값이고 {@code status}(주문 상태)를 대체하지 않는다.
 * {@code lineItems}는 카드의 옵션 줄용 — {@code rewardSummary}("리워드명 외 N건")만으로는 채울 수 없다.
 * {@code refundRequests}는 이 주문의 취소·반품·교환 신청 이력(없으면 빈 배열, 조회 실패 시에도 빈 배열).
 */
public record OrderSummaryResponse(
        UUID orderId, UUID projectId, String projectTitle, String status, String progressStage, long discountAmount,
        long finalAmount, Instant createdAt, Instant paidAt, String sellerDisplayName, String thumbnailUrl,
        String rewardSummary, int totalQuantity, List<OrderLineItemDetailResponse> lineItems,
        List<String> availableActions, List<RefundRequestStatusResponse> refundRequests
) {

    public static OrderSummaryResponse from(OrderListItem item) {
        Funding funding = item.funding();
        ProjectSummaryClient.ProjectSummary summary = item.projectSummary();
        List<FundingLineItem> lineItems = funding.getLineItems();
        long finalAmount = funding.totalRewardAmount() + funding.getShippingFee() - item.discountAmount();
        return new OrderSummaryResponse(funding.getPublicId(), funding.getProjectId(), funding.getProjectTitle(),
                funding.getStatus().name(), item.progressStage().name(), item.discountAmount(), finalAmount,
                funding.getCreatedAt(), funding.getPaidAt(),
                summary == null ? null : summary.sellerDisplayName(), summary == null ? null : summary.thumbnailUrl(),
                rewardSummary(lineItems), lineItems.stream().mapToInt(FundingLineItem::quantity).sum(),
                lineItems.stream().map(OrderLineItemDetailResponse::from).toList(),
                item.availableActions(),
                item.refundRequests().stream().map(RefundRequestStatusResponse::from).toList());
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
