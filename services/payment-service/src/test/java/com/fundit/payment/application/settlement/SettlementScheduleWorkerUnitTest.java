package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementScheduleWorkerUnitTest {

    @Mock
    private SettlementScheduleJpaRepository settlementScheduleJpaRepository;
    @Mock
    private SettlementBatchGenerationService settlementBatchGenerationService;

    private SettlementScheduleWorker settlementScheduleWorker;

    @BeforeEach
    void setUp() {
        settlementScheduleWorker = new SettlementScheduleWorker(settlementScheduleJpaRepository,
                settlementBatchGenerationService);
    }

    @Test
    void 도래한_스케줄을_판매자별로_묶어_배치생성을_위임한다() {
        // given
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        SettlementScheduleJpaEntity a1 = schedule(sellerA, SettlementScheduleJpaEntity.TYPE_INTERIM);
        SettlementScheduleJpaEntity a2 = schedule(sellerA, SettlementScheduleJpaEntity.TYPE_INTERIM);
        SettlementScheduleJpaEntity b1 = schedule(sellerB, SettlementScheduleJpaEntity.TYPE_INTERIM);
        SettlementScheduleJpaEntity finalEntry = schedule(sellerA, SettlementScheduleJpaEntity.TYPE_FINAL);
        when(settlementScheduleJpaRepository.findByBatchTypeAndProcessedAtIsNullAndDueAtLessThanEqual(
                eq(SettlementScheduleJpaEntity.TYPE_INTERIM), any()))
                .thenReturn(List.of(a1, a2, b1));
        when(settlementScheduleJpaRepository.findByBatchTypeAndProcessedAtIsNullAndDueAtLessThanEqual(
                eq(SettlementScheduleJpaEntity.TYPE_FINAL), any()))
                .thenReturn(List.of(finalEntry));

        // when
        settlementScheduleWorker.run();

        // then
        verify(settlementBatchGenerationService).generate(eq(sellerA), eq(SettlementBatchType.INTERIM),
                org.mockito.ArgumentMatchers.argThat(list -> list.size() == 2 && list.contains(a1) && list.contains(a2)));
        verify(settlementBatchGenerationService).generate(sellerB, SettlementBatchType.INTERIM, List.of(b1));
        verify(settlementBatchGenerationService).generate(sellerA, SettlementBatchType.FINAL, List.of(finalEntry));
    }

    private SettlementScheduleJpaEntity schedule(UUID sellerId, String batchType) {
        return SettlementScheduleJpaEntity.builder()
                .fundingId(1L)
                .projectId(10L)
                .sellerId(sellerId)
                .batchType(batchType)
                .dueAt(Instant.now())
                .build();
    }
}
