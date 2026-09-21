package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.settlement.SettlementDisputeService.DisputeSummary;

import java.time.Instant;

public record SettlementDisputeSummaryResponse(
        Long disputeId, Long settlementBatchId, String reason, String status, Instant requestedAt, Instant resolvedAt
) {

    public static SettlementDisputeSummaryResponse from(DisputeSummary summary) {
        return new SettlementDisputeSummaryResponse(summary.disputeId(), summary.settlementBatchId(), summary.reason(),
                summary.status(), summary.requestedAt(), summary.resolvedAt());
    }
}
