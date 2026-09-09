package com.fundit.payment.infrastructure.event;

import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventOutboxWorkerUnitTest {

    @Mock
    private PaymentEventOutboxJpaRepository outboxRepository;
    @Mock
    private PaymentEventTransport transport;

    private PaymentEventOutboxWorker worker;

    private void setUp() {
        worker = new PaymentEventOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("couponIssuanceId", 7L);
        payload.put("paidAt", "2026-09-08T14:23:11Z");
        PaymentEventOutboxJpaEntity event = PaymentEventOutboxJpaEntity.builder()
                .eventType(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED)
                .fundingId(1024L)
                .payload(payload)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<PaymentEventTransport.PaymentCompletedTransportEvent> captor =
                ArgumentCaptor.forClass(PaymentEventTransport.PaymentCompletedTransportEvent.class);
        verify(transport).sendPaymentCompleted(captor.capture());
        assertThat(captor.getValue().fundingId()).isEqualTo(1024L);
        assertThat(captor.getValue().couponIssuanceId()).isEqualTo(7L);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        PaymentEventOutboxJpaEntity event = PaymentEventOutboxJpaEntity.builder()
                .eventType(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED)
                .fundingId(1024L)
                .payload(Map.of())
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 미구성")).when(transport).sendPaymentCompleted(any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }
}
