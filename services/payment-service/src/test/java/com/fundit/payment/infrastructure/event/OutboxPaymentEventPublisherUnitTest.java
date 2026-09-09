package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPaymentEventPublisherUnitTest {

    @Mock
    private PaymentEventOutboxJpaRepository outboxRepository;

    private OutboxPaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPaymentEventPublisher(outboxRepository);
    }

    @Test
    void 결제완료_이벤트를_아웃박스에_적재한다() {
        UUID paymentId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-09-08T01:00:00Z");

        publisher.publishPaymentCompleted(new PaymentEventPublisher.PaymentCompletedEvent(
                paymentId, 1024L, 7L, paidAt));

        ArgumentCaptor<PaymentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(PaymentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        PaymentEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED);
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getFundingId()).isEqualTo(1024L);
        assertThat(saved.getPayload().get("couponIssuanceId")).isEqualTo(7L);
        assertThat(saved.getPayload().get("paidAt")).isEqualTo(paidAt.toString());
    }

    @Test
    void paidAt이_없으면_payload에_null을_넣는다() {
        publisher.publishPaymentCompleted(new PaymentEventPublisher.PaymentCompletedEvent(
                UUID.randomUUID(), 1L, null, null));

        ArgumentCaptor<PaymentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(PaymentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload().get("paidAt")).isNull();
        assertThat(captor.getValue().getPayload().get("couponIssuanceId")).isNull();
    }

    @Test
    void 환불완료_이벤트를_아웃박스에_적재한다() {
        UUID paymentId = UUID.randomUUID();

        publisher.publishRefundCompleted(new PaymentEventPublisher.RefundCompletedEvent(
                paymentId, 1024L, 7L, PaymentEventPublisher.RefundReason.POST_SUCCESS_DEFECT, false));

        ArgumentCaptor<PaymentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(PaymentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        PaymentEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(PaymentEventOutboxJpaEntity.TYPE_REFUND_COMPLETED);
        assertThat(saved.getPayload().get("refundReason")).isEqualTo("POST_SUCCESS_DEFECT");
        assertThat(saved.getPayload().get("fullRefund")).isEqualTo(false);
    }
}
