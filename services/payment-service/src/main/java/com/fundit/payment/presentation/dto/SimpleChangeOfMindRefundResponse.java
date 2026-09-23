package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.SimpleChangeOfMindRefundService.SimpleChangeOfMindRefundResult;

public record SimpleChangeOfMindRefundResponse(Long refundId, String status) {

    public static SimpleChangeOfMindRefundResponse from(SimpleChangeOfMindRefundResult result) {
        return new SimpleChangeOfMindRefundResponse(result.refundId(), result.status());
    }
}
