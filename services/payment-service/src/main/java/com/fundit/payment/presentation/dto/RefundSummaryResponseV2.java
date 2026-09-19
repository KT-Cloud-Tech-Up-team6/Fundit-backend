package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.OrderSummaryClient;
import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PAYMENT-003 v2 응답 — fundingId가 order-service publicId(UUID)로 채워진다.
 * {@code projectTitle}/{@code lineItems}는 order-service 조회 실패 시 null일 수 있다(V04, 부가 정보).
 */
public record RefundSummaryResponseV2(Long refundId, UUID fundingId, String triggerType, String status, long amount,
                                       Instant requestedAt, String reasonDetail, String rejectedReason,
                                       Instant completedAt, String projectTitle,
                                       List<RefundLineItemResponse> lineItems) {

    public static RefundSummaryResponseV2 from(RefundSummary summary) {
        OrderSummaryClient.OrderSummary orderSummary = summary.orderSummary();
        return new RefundSummaryResponseV2(summary.refundId(), summary.fundingId(), summary.triggerType(),
                summary.status(), summary.amount(), summary.requestedAt(), summary.reasonDetail(),
                summary.rejectedReason(), summary.completedAt(),
                orderSummary == null ? null : orderSummary.projectTitle(),
                orderSummary == null ? null : orderSummary.lineItems().stream()
                        .map(RefundLineItemResponse::from).toList());
    }

    public record RefundLineItemResponse(String rewardName, int quantity, long unitPrice) {

        public static RefundLineItemResponse from(OrderSummaryClient.LineItem lineItem) {
            return new RefundLineItemResponse(lineItem.rewardName(), lineItem.quantity(), lineItem.unitPrice());
        }
    }
}
