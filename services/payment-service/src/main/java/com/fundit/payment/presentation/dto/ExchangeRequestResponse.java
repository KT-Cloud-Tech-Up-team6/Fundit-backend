package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.ExchangeRequestService.ExchangeRequestResult;

public record ExchangeRequestResponse(Long refundId, String status) {

    public static ExchangeRequestResponse from(ExchangeRequestResult result) {
        return new ExchangeRequestResponse(result.refundId(), result.status());
    }
}
