package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SettlementScheduleJpaRepository extends JpaRepository<SettlementScheduleJpaEntity, Long> {

    List<SettlementScheduleJpaEntity> findByBatchTypeAndProcessedAtIsNullAndDueAtLessThanEqual(
            String batchType, Instant now);

    /** Kafka at-least-once 재전달로 같은 이벤트가 두 번 와도 실행 대상을 중복 등록하지 않기 위한 멱등 가드. */
    boolean existsByFundingIdAndBatchType(Long fundingId, String batchType);
}
