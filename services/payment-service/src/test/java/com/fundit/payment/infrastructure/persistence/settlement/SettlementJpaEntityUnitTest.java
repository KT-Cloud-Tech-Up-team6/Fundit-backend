package com.fundit.payment.infrastructure.persistence.settlement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementJpaEntityUnitTest {

    @Test
    void 배치와_스케줄과_보류와_이의의_생성시각을_채운다() {
        SettlementBatchJpaEntity batch = SettlementBatchJpaEntity.builder()
                .sellerId(UUID.randomUUID())
                .batchType("INTERIM")
                .status("PENDING")
                .periodStart(Instant.now())
                .periodEnd(Instant.now())
                .grossAmount(1L)
                .platformFeeAmount(0L)
                .refundDeductionAmount(0L)
                .couponDeductionAmount(0L)
                .totalAmount(1L)
                .build();
        batch.onCreate();
        assertThat(batch.getCreatedAt()).isNotNull();

        SettlementScheduleJpaEntity schedule = SettlementScheduleJpaEntity.builder()
                .fundingId(1L)
                .projectId(1L)
                .sellerId(UUID.randomUUID())
                .batchType("INTERIM")
                .dueAt(Instant.now())
                .build();
        schedule.onCreate();
        assertThat(schedule.getCreatedAt()).isNotNull();
        schedule.markProcessed();
        assertThat(schedule.getProcessedAt()).isNotNull();

        SettlementHoldJpaEntity hold = SettlementHoldJpaEntity.builder()
                .fundingId(1L)
                .paymentId(UUID.randomUUID())
                .holdAmount(1000L)
                .build();
        hold.onCreate();
        assertThat(hold.getCreatedAt()).isNotNull();
        assertThat(hold.isHolding()).isTrue();

        SettlementDisputeJpaEntity dispute = SettlementDisputeJpaEntity.builder()
                .batchId(1L)
                .sellerId(UUID.randomUUID())
                .reason("다름")
                .build();
        dispute.onCreate();
        assertThat(dispute.getRequestedAt()).isNotNull();
    }
}
