package com.fundit.payment.domain.settlement;

import java.util.UUID;

/** SettlementBatch 애그리거트에 속한 자식 값 객체 — FundingLineItem과 동일한 취급(별도 Repository 없음). */
public record SettlementBatchItem(Long id, Long fundingId, UUID paymentId, long amount) {

    public static SettlementBatchItem of(Long fundingId, UUID paymentId, long amount) {
        return new SettlementBatchItem(null, fundingId, paymentId, amount);
    }
}
