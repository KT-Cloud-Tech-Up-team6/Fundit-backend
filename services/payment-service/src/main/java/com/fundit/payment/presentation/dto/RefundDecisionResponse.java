package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.DefectRefundDecisionService.DefectDecisionResult;

public record RefundDecisionResponse(Long refundId, String status) {

    public static RefundDecisionResponse from(DefectDecisionResult result) {
        return new RefundDecisionResponse(result.refundId(), result.status());
    }
}
