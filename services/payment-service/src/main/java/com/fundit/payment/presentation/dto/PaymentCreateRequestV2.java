package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** PAYMENT-001 v2 요청 — fundingId는 order-service publicId(UUID). */
public record PaymentCreateRequestV2(@NotNull UUID fundingId) {
}
