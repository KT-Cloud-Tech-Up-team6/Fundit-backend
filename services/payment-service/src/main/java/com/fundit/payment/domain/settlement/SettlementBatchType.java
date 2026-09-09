package com.fundit.payment.domain.settlement;

public enum SettlementBatchType {
    /** 선정산 — 목표달성확정일+5영업일. */
    INTERIM,
    /** 최종정산 — 배송완료+14일. */
    FINAL
}
