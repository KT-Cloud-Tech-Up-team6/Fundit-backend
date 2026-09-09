package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.payment.PaymentCreateService.PaymentCreateResult;

import java.util.UUID;

/** PAYMENT-001 응답 — 결제위젯 초기화·requestPayment 호출에 필요한 값(customerKey는 응답에 포함하지 않음). */
public record PaymentCreateResponse(UUID paymentId, String pgOrderId, long amount, String orderName) {

    public static PaymentCreateResponse from(PaymentCreateResult result) {
        return new PaymentCreateResponse(result.paymentId(), result.pgOrderId(), result.amount(), result.orderName());
    }
}
