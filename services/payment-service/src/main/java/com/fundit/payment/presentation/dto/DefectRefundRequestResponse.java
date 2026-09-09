package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.DefectRefundRequestService.DefectRefundRequestResult;

public record DefectRefundRequestResponse(Long refundId, String status) {

    public static DefectRefundRequestResponse from(DefectRefundRequestResult result) {
        return new DefectRefundRequestResponse(result.refundId(), result.status());
    }
}
