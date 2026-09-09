package com.fundit.payment.infrastructure.persistence.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentJpaEntityUnitTest {

    @Test
    void persist_전에_생성시각을_채운다() {
        PaymentJpaEntity entity = PaymentJpaEntity.builder()
                .id(UUID.randomUUID())
                .fundingId(1L)
                .memberId(UUID.randomUUID())
                .pgOrderId("fundit-1")
                .amount(1000L)
                .orderName("주문")
                .status("PENDING")
                .idempotencyKey("idem")
                .build();

        entity.onCreate();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();

        var createdAt = entity.getCreatedAt();
        entity.onUpdate();
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
