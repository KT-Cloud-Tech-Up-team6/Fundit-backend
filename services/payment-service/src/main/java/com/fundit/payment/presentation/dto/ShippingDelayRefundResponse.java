package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.ShippingDelayRefundService.ShippingDelayRefundResult;

public record ShippingDelayRefundResponse(Long refundId, String status) {

    public static ShippingDelayRefundResponse from(ShippingDelayRefundResult result) {
        return new ShippingDelayRefundResponse(result.refundId(), result.status());
    }
}
