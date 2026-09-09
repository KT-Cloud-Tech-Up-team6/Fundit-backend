package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** PAYMENT-001 요청. */
public record PaymentCreateRequest(@NotNull Long fundingId) {
}
