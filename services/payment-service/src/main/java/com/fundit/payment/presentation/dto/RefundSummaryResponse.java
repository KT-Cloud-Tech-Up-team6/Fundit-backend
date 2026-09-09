package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;

import java.time.Instant;

/** PAYMENT-003 응답. */
public record RefundSummaryResponse(Long refundId, Long fundingId, String triggerType, String status, long amount,
                                     Instant requestedAt) {

    public static RefundSummaryResponse from(RefundSummary summary) {
        return new RefundSummaryResponse(summary.refundId(), summary.fundingId(), summary.triggerType(),
                summary.status(), summary.amount(), summary.requestedAt());
    }
}
