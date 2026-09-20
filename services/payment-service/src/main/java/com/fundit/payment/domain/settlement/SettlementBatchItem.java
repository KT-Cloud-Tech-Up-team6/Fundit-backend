package com.fundit.payment.domain.settlement;

import java.util.UUID;

/**
 * SettlementBatch 애그리거트에 속한 자식 값 객체 — FundingLineItem과 동일한 취급(별도 Repository 없음).
 * {@code payoutAmount}는 이 건에 대해 실제 지급 확정된 금액(INTERIM=순액의 70%, FINAL=순액-기지급 INTERIM)이며,
 * FINAL 배치가 동일 펀딩의 이전 INTERIM 지급액을 조회할 때 이 값을 합산한다.
 */
public record SettlementBatchItem(Long id, Long fundingId, UUID paymentId, long amount, long payoutAmount) {

    public static SettlementBatchItem of(Long fundingId, UUID paymentId, long amount, long payoutAmount) {
        return new SettlementBatchItem(null, fundingId, paymentId, amount, payoutAmount);
    }
}
