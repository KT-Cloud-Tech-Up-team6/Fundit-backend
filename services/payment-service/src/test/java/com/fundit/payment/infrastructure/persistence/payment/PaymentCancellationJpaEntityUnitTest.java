package com.fundit.payment.infrastructure.persistence.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCancellationJpaEntityUnitTest {

    @Test
    void persist_전에_취소시각을_채운다() {
        PaymentCancellationJpaEntity entity = PaymentCancellationJpaEntity.builder()
                .paymentId(UUID.randomUUID())
                .pgTransactionKey("tx")
                .cancelAmount(1000L)
                .cancelReason("취소")
                .build();
        entity.onCreate();
        assertThat(entity.getCanceledAt()).isNotNull();
    }
}
