package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;

import java.time.Instant;
import java.util.UUID;

/** PAYMENT-003 v2 응답 — fundingId가 order-service publicId(UUID)로 채워진다. */
public record RefundSummaryResponseV2(Long refundId, UUID fundingId, String triggerType, String status, long amount,
                                       Instant requestedAt) {

    public static RefundSummaryResponseV2 from(RefundSummary summary) {
        return new RefundSummaryResponseV2(summary.refundId(), summary.fundingId(), summary.triggerType(),
                summary.status(), summary.amount(), summary.requestedAt());
    }
}
