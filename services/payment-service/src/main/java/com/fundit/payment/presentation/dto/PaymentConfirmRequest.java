package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** PAYMENT-002 요청 — successUrl 리다이렉트로 받은 쿼리 파라미터를 그대로 전달. */
public record PaymentConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId, @Positive long amount) {
}
