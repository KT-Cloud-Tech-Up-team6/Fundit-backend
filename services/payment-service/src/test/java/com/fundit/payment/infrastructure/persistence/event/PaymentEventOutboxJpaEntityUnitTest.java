package com.fundit.payment.infrastructure.persistence.event;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventOutboxJpaEntityUnitTest {

    @Test
    void persist와_발행_성공_실패를_기록한다() {
        PaymentEventOutboxJpaEntity event = PaymentEventOutboxJpaEntity.builder()
                .eventType(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED)
                .paymentId(UUID.randomUUID())
                .fundingId(1L)
                .payload(Map.of())
                .build();
        event.onCreate();
        assertThat(event.getCreatedAt()).isNotNull();

        event.recordFailure("timeout");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("timeout");

        event.markPublished();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }
}
