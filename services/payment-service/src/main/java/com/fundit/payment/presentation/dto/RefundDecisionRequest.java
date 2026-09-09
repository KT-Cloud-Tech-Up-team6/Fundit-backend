package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** PAYMENT-007 요청. */
public record RefundDecisionRequest(@NotNull Decision decision, String reason) {

    public boolean isApproved() {
        return decision == Decision.APPROVED;
    }

    public enum Decision {
        APPROVED, REJECTED
    }
}
