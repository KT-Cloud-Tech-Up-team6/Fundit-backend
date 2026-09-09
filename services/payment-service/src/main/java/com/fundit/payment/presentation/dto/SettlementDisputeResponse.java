package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.settlement.SettlementDisputeService.DisputeResult;

public record SettlementDisputeResponse(Long disputeId, String status) {

    public static SettlementDisputeResponse from(DisputeResult result) {
        return new SettlementDisputeResponse(result.disputeId(), result.status());
    }
}
