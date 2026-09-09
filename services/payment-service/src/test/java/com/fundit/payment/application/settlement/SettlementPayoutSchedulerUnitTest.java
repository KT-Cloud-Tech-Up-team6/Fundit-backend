package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementPayoutSchedulerUnitTest {

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    private SettlementPayoutScheduler settlementPayoutScheduler;

    @BeforeEach
    void setUp() {
        settlementPayoutScheduler = new SettlementPayoutScheduler(settlementBatchRepository);
    }

    private SettlementBatch pendingBatch() {
        return SettlementBatch.create(UUID.randomUUID(), SettlementBatchType.INTERIM,
                Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of());
    }

    @Test
    void 지급대상_배치는_PAID로_전이된다() {
        // given
        SettlementBatch batch = pendingBatch();
        when(settlementBatchRepository.findPayable()).thenReturn(List.of(batch));

        // when
        settlementPayoutScheduler.run();

        // then
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.PAID);
        assertThat(batch.getProcessedAt()).isNotNull();
        verify(settlementBatchRepository).save(batch);
    }

    @Test
    void 한_건이_실패해도_나머지_건은_계속_처리된다() {
        // given
        SettlementBatch first = pendingBatch();
        SettlementBatch second = pendingBatch();
        when(settlementBatchRepository.findPayable()).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("계좌 오류")).when(settlementBatchRepository).save(first);

        // when
        settlementPayoutScheduler.run();

        // then
        verify(settlementBatchRepository).save(second);
        assertThat(second.getStatus()).isEqualTo(SettlementBatchStatus.PAID);
    }
}
