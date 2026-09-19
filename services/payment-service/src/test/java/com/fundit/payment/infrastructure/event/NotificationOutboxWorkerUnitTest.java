package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerUnitTest {

    @Mock
    private NotificationOutboxJpaRepository outboxRepository;
    @Mock
    private PaymentNotificationTransport transport;

    private NotificationOutboxWorker worker;

    private void setUp() {
        worker = new NotificationOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();
        NotificationOutboxJpaEntity event = NotificationOutboxJpaEntity.builder()
                .memberId(memberId).fundingId(new UUID(0L, 1024L)).status("COMPLETED").build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<RefundStatusChangedEvent> captor = ArgumentCaptor.forClass(RefundStatusChangedEvent.class);
        verify(transport).sendRefundStatusChanged(captor.capture(), any());
        assertThat(captor.getValue().fundingId()).isEqualTo(new UUID(0L, 1024L));
        assertThat(captor.getValue().memberId()).isEqualTo(memberId);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        NotificationOutboxJpaEntity event = NotificationOutboxJpaEntity.builder()
                .memberId(UUID.randomUUID()).fundingId(new UUID(0L, 1024L)).status("COMPLETED").build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 오류")).when(transport).sendRefundStatusChanged(any(), any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 오류");
    }
}
