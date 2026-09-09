package com.fundit.payment.domain.settlement;

public enum SettlementBatchStatus {
    PENDING,
    /** PAYMENT-011 이의신청 접수 시 전환 — 해당 회차 지급 보류. */
    ON_HOLD,
    PAID
}
