package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** PAYMENT-008 요청. */
public record ShippingDelayRefundRequest(@NotNull Long fundingId) {
}
