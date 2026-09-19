package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.OrderSummaryClient;
import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;
import com.fundit.payment.presentation.dto.RefundSummaryResponseV2.RefundLineItemResponse;

import java.time.Instant;
import java.util.List;

/**
 * PAYMENT-003 v1 응답. 결제 도메인은 UUID만 저장해 {@code fundingId}(Long)는 항상 {@code null}이다.
 * 실제 식별자가 필요하면 {@link RefundSummaryResponseV2}를 쓸 것.
 * {@code projectTitle}/{@code lineItems}는 order-service 조회 실패 시 null일 수 있다(V04, 부가 정보).
 */
public record RefundSummaryResponse(Long refundId, Long fundingId, String triggerType, String status, long amount,
                                     Instant requestedAt, String reasonDetail, String rejectedReason,
                                     Instant completedAt, String projectTitle, List<RefundLineItemResponse> lineItems) {

    public static RefundSummaryResponse from(RefundSummary summary) {
        OrderSummaryClient.OrderSummary orderSummary = summary.orderSummary();
        return new RefundSummaryResponse(summary.refundId(), null, summary.triggerType(),
                summary.status(), summary.amount(), summary.requestedAt(), summary.reasonDetail(),
                summary.rejectedReason(), summary.completedAt(),
                orderSummary == null ? null : orderSummary.projectTitle(),
                orderSummary == null ? null : orderSummary.lineItems().stream()
                        .map(RefundLineItemResponse::from).toList());
    }
}
