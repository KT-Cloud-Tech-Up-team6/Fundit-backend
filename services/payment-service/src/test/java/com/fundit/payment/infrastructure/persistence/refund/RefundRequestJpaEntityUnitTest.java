package com.fundit.payment.infrastructure.persistence.refund;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestJpaEntityUnitTest {

    @Test
    void persist_전에_신청시각을_채운다() {
        RefundRequestJpaEntity entity = RefundRequestJpaEntity.builder()
                .fundingId(1L)
                .paymentId(UUID.randomUUID())
                .triggerType("DEFECT")
                .status("REQUESTED")
                .build();

        entity.onCreate();
        assertThat(entity.getRequestedAt()).isNotNull();
    }
}
