package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;

public record ExchangeRequestResponse(Long refundId, String status) {

    public static ExchangeRequestResponse from(PostShipmentRefundRequestResult result) {
        return new ExchangeRequestResponse(result.refundId(), result.status());
    }
}
