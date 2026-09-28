package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.ExchangeFeePaymentService.ExchangeFeePaymentResult;

import java.util.UUID;

/**
 * 교환 배송비 결제 시도 생성 응답 — 결제위젯 {@code requestPayment({orderId, amount, orderName})}에
 * 그대로 넘기는 값이다. 승인은 리워드 결제와 같은 {@code POST /api/v2/payments/confirm}을 쓴다.
 */
public record ExchangeFeePaymentResponse(UUID paymentId, String pgOrderId, long amount, String orderName) {

    public static ExchangeFeePaymentResponse from(ExchangeFeePaymentResult result) {
        return new ExchangeFeePaymentResponse(result.paymentId(), result.pgOrderId(), result.amount(),
                result.orderName());
    }
}
