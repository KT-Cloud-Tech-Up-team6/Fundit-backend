package com.fundit.payment.infrastructure.persistence.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementBatchMapperUnitTest {

    private final SettlementBatchMapper mapper = new SettlementBatchMapper();

    @Test
    void 배치와_항목을_도메인으로_복원한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T01:00:00Z");
        SettlementBatchJpaEntity entity = SettlementBatchJpaEntity.builder()
                .id(77L)
                .sellerId(sellerId)
                .batchType(SettlementBatchType.FINAL.name())
                .status(SettlementBatchStatus.PENDING.name())
                .periodStart(now)
                .periodEnd(now)
                .grossAmount(100_000L)
                .platformFeeAmount(3_000L)
                .refundDeductionAmount(1_000L)
                .couponDeductionAmount(2_000L)
                .totalAmount(94_000L)
                .processedAt(null)
                .createdAt(now)
                .build();
        List<SettlementBatchItemJpaEntity> items = List.of(SettlementBatchItemJpaEntity.builder()
                .id(1L)
                .batchId(77L)
                .fundingId(1024L)
                .paymentId(paymentId)
                .amount(94_000L)
                .build());

        // when
        SettlementBatch domain = mapper.toDomain(entity, items);

        // then
        assertThat(domain.getId()).isEqualTo(77L);
        assertThat(domain.getBatchType()).isEqualTo(SettlementBatchType.FINAL);
        assertThat(domain.getItems()).singleElement().satisfies(item -> {
            assertThat(item.fundingId()).isEqualTo(1024L);
            assertThat(item.paymentId()).isEqualTo(paymentId);
            assertThat(item.amount()).isEqualTo(94_000L);
        });
    }

    @Test
    void 도메인을_엔티티와_항목으로_변환한다() {
        UUID sellerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        SettlementBatch domain = SettlementBatch.create(sellerId, SettlementBatchType.INTERIM, now, now,
                        100_000L, 3_000L, 0L, 0L, List.of(SettlementBatchItem.of(1024L, paymentId, 97_000L)))
                .toBuilder().id(77L).processedAt(now).createdAt(now).build();

        SettlementBatchJpaEntity entity = mapper.toEntity(domain);
        SettlementBatchItemJpaEntity itemEntity = mapper.toItemEntity(77L, domain.getItems().get(0));

        assertThat(entity.getSellerId()).isEqualTo(sellerId);
        assertThat(entity.getBatchType()).isEqualTo("INTERIM");
        assertThat(entity.getTotalAmount()).isEqualTo(97_000L);
        assertThat(itemEntity.getBatchId()).isEqualTo(77L);
        assertThat(itemEntity.getFundingId()).isEqualTo(1024L);
        assertThat(itemEntity.getPaymentId()).isEqualTo(paymentId);
    }
}
