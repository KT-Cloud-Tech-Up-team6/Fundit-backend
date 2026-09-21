package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.settlement.SettlementQueryService.SettlementBatchSummary;

import java.time.Instant;

/** 정산 목록 — settlementBatchId를 확인해 상세(PAYMENT-009) 조회로 이어가는 진입점. */
public record SettlementBatchSummaryResponse(
        Long settlementBatchId, String batchType, String status, Instant periodStart, Instant periodEnd, long totalAmount
) {

    public static SettlementBatchSummaryResponse from(SettlementBatchSummary summary) {
        return new SettlementBatchSummaryResponse(summary.settlementBatchId(), summary.batchType(), summary.status(),
                summary.periodStart(), summary.periodEnd(), summary.totalAmount());
    }
}
