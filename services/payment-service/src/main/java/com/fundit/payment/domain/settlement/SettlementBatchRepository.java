package com.fundit.payment.domain.settlement;

import java.util.List;
import java.util.Optional;

public interface SettlementBatchRepository {

    SettlementBatch save(SettlementBatch settlementBatch);

    Optional<SettlementBatch> findById(Long id);

    /** PAYMENT-015 — 매주 금요일 지급 대상(PENDING만, ON_HOLD 제외). */
    List<SettlementBatch> findPayable();

    /** PAYMENT-014 — 동일 펀딩에 대해 이전 INTERIM 배치로 이미 지급 확정된 금액(없으면 0). */
    long sumInterimPayoutByFundingId(Long fundingId);
}
