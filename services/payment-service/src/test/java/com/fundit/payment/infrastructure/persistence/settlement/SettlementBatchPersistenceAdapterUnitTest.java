package com.fundit.payment.infrastructure.persistence.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchPersistenceAdapterUnitTest {

    @Mock
    private SettlementBatchJpaRepository batchJpaRepository;
    @Mock
    private SettlementBatchItemJpaRepository itemJpaRepository;

    private final SettlementBatchMapper mapper = new SettlementBatchMapper();
    private SettlementBatchPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SettlementBatchPersistenceAdapter(batchJpaRepository, itemJpaRepository, mapper);
    }

    @Test
    void 신규_저장이면_항목도_함께_적재한다() {
        UUID sellerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        SettlementBatch domain = SettlementBatch.create(sellerId, SettlementBatchType.INTERIM, now, now,
                100_000L, 3_000L, 0L, 0L, List.of(SettlementBatchItem.of(1024L, paymentId, 97_000L)));
        SettlementBatchJpaEntity savedEntity = mapper.toEntity(domain.toBuilder().id(77L).createdAt(now).build());
        when(batchJpaRepository.save(any())).thenReturn(savedEntity);
        when(itemJpaRepository.findByBatchId(77L)).thenReturn(List.of(
                mapper.toItemEntity(77L, domain.getItems().get(0))));

        SettlementBatch saved = adapter.save(domain);

        assertThat(saved.getId()).isEqualTo(77L);
        verify(itemJpaRepository).save(any());
        assertThat(saved.getItems()).hasSize(1);
    }

    @Test
    void 기존_배치_저장이면_항목을_다시_넣지_않는다() {
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.now();
        SettlementBatch domain = SettlementBatch.create(sellerId, SettlementBatchType.INTERIM, now, now,
                        100_000L, 3_000L, 0L, 0L, List.of())
                .toBuilder().id(77L).createdAt(now).build();
        when(batchJpaRepository.save(any())).thenReturn(mapper.toEntity(domain));
        when(itemJpaRepository.findByBatchId(77L)).thenReturn(List.of());

        adapter.save(domain);

        verify(itemJpaRepository, never()).save(any());
    }

    @Test
    void 조회와_지급대상_목록은_항목을_붙여_반환한다() {
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.now();
        SettlementBatchJpaEntity entity = mapper.toEntity(SettlementBatch.create(sellerId, SettlementBatchType.FINAL,
                        now, now, 10_000L, 300L, 0L, 0L, List.of())
                .toBuilder().id(5L).createdAt(now).build());
        when(batchJpaRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(batchJpaRepository.findByStatus(SettlementBatchStatus.PENDING.name())).thenReturn(List.of(entity));
        when(itemJpaRepository.findByBatchId(5L)).thenReturn(List.of());

        assertThat(adapter.findById(5L)).isPresent();
        assertThat(adapter.findPayable()).hasSize(1);
        assertThat(adapter.findById(99L)).isEmpty();
    }
}
