package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PAYMENT-013/014 실행부 — {@code settlement_schedule}에서 도래한(due_at &lt;= now) 미처리 건을
 * 판매자 단위로 묶어 {@link SettlementBatchGenerationService}에 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class SettlementScheduleWorker {

    private final SettlementScheduleJpaRepository settlementScheduleJpaRepository;
    private final SettlementBatchGenerationService settlementBatchGenerationService;

    @Scheduled(cron = "${settlement.schedule.cron:0 0 3 * * *}")
    public void run() {
        processDue(SettlementScheduleJpaEntity.TYPE_INTERIM, SettlementBatchType.INTERIM);
        processDue(SettlementScheduleJpaEntity.TYPE_FINAL, SettlementBatchType.FINAL);
    }

    private void processDue(String scheduleType, SettlementBatchType batchType) {
        List<SettlementScheduleJpaEntity> due = settlementScheduleJpaRepository
                .findByBatchTypeAndProcessedAtIsNullAndDueAtLessThanEqual(scheduleType, Instant.now());
        Map<UUID, List<SettlementScheduleJpaEntity>> bySeller = due.stream()
                .collect(Collectors.groupingBy(SettlementScheduleJpaEntity::getSellerId));
        bySeller.forEach((sellerId, entries) -> settlementBatchGenerationService.generate(sellerId, batchType, entries));
    }
}
