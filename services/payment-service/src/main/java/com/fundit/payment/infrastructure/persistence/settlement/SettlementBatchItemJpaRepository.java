package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementBatchItemJpaRepository extends JpaRepository<SettlementBatchItemJpaEntity, Long> {

    List<SettlementBatchItemJpaEntity> findByBatchId(Long batchId);
}
