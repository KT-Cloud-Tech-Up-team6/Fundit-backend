package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentEventOutboxWorkerUnitTest {

    @Mock
    private FulfillmentEventOutboxJpaRepository outboxRepository;
    @Mock
    private FulfillmentNotificationTransport transport;

    private FulfillmentEventOutboxWorker worker;

    private void setUp() {
        worker = new FulfillmentEventOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        FulfillmentEventOutboxJpaEntity event = FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER)
                .projectId(123L)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<StaleUpdateReminderEvent> captor = ArgumentCaptor.forClass(StaleUpdateReminderEvent.class);
        verify(transport).sendStaleUpdateReminder(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(123L);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        FulfillmentEventOutboxJpaEntity event = FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER)
                .projectId(123L)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 미구성")).when(transport).sendStaleUpdateReminder(any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }

    @Test
    void 알_수_없는_이벤트_타입은_실패로_기록한다() {
        // given
        setUp();
        FulfillmentEventOutboxJpaEntity event = FulfillmentEventOutboxJpaEntity.builder()
                .eventType("UnknownType")
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("알 수 없는 알림 이벤트 타입");
    }
}
