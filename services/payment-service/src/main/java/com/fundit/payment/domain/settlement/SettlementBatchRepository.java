package com.fundit.payment.domain.settlement;

import java.util.List;
import java.util.Optional;

public interface SettlementBatchRepository {

    SettlementBatch save(SettlementBatch settlementBatch);

    Optional<SettlementBatch> findById(Long id);

    /** PAYMENT-015 — 매주 금요일 지급 대상(PENDING만, ON_HOLD 제외). */
    List<SettlementBatch> findPayable();
}
