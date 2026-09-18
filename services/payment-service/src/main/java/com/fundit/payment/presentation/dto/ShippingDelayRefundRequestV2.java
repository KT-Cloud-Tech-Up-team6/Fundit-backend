package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** PAYMENT-008 v2 요청 — fundingId는 order-service publicId(UUID). */
public record ShippingDelayRefundRequestV2(@NotNull UUID fundingId) {
}
