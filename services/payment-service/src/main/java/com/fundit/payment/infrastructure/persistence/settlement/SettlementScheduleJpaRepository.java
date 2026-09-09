package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SettlementScheduleJpaRepository extends JpaRepository<SettlementScheduleJpaEntity, Long> {

    List<SettlementScheduleJpaEntity> findByBatchTypeAndProcessedAtIsNullAndDueAtLessThanEqual(
            String batchType, Instant now);
}
