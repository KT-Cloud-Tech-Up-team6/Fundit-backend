package com.fundit.fulfillment.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** payment-service 교환 재발송 요청 — {@code refundRequestId}는 재요청 멱등 판정에 쓰인다. */
public record ReshipmentInternalRequest(@NotNull Long refundRequestId) {
}
