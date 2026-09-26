package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;

public record DefectRefundRequestResponse(Long refundId, String status) {

    public static DefectRefundRequestResponse from(PostShipmentRefundRequestResult result) {
        return new DefectRefundRequestResponse(result.refundId(), result.status());
    }
}
