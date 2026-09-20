package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementBatchItemJpaRepository extends JpaRepository<SettlementBatchItemJpaEntity, Long> {

    List<SettlementBatchItemJpaEntity> findByBatchId(Long batchId);

    /** PAYMENT-014 — 동일 펀딩에 대해 이전 INTERIM 배치로 이미 지급 확정된 금액 합계. */
    @Query("SELECT COALESCE(SUM(i.payoutAmount), 0) FROM SettlementBatchItemJpaEntity i, SettlementBatchJpaEntity b "
            + "WHERE i.batchId = b.id AND i.fundingId = :fundingId AND b.batchType = 'INTERIM'")
    long sumInterimPayoutAmountByFundingId(@Param("fundingId") Long fundingId);
}
