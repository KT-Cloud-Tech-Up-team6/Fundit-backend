package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;

import java.time.Instant;

/**
 * PAYMENT-003 v1 응답. 결제 도메인은 UUID만 저장해 {@code fundingId}(Long)는 항상 {@code null}이다.
 * 실제 식별자가 필요하면 {@link RefundSummaryResponseV2}를 쓸 것.
 */
public record RefundSummaryResponse(Long refundId, Long fundingId, String triggerType, String status, long amount,
                                     Instant requestedAt) {

    public static RefundSummaryResponse from(RefundSummary summary) {
        return new RefundSummaryResponse(summary.refundId(), null, summary.triggerType(),
                summary.status(), summary.amount(), summary.requestedAt());
    }
}
