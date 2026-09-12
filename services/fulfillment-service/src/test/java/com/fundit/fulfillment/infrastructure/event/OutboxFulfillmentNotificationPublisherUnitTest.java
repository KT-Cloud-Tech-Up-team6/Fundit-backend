package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxFulfillmentNotificationPublisherUnitTest {

    @Mock
    private FulfillmentEventOutboxJpaRepository outboxRepository;

    private OutboxFulfillmentNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxFulfillmentNotificationPublisher(outboxRepository);
    }

    @Test
    void 미등록_알림을_아웃박스에_적재한다() {
        // when
        publisher.publishStaleUpdateReminder(new StaleUpdateReminderEvent(123L));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER);
        assertThat(captor.getValue().getProjectId()).isEqualTo(123L);
    }

    @Test
    void 일정변경_알림을_아웃박스에_적재한다() {
        // given
        Instant newPlannedDate = Instant.parse("2026-09-10T00:00:00Z");

        // when
        publisher.publishScheduleChanged(new ScheduleChangedEvent(123L, FulfillmentStage.SHIPPING_OUT,
                ScheduleChangeReasonType.STOCK_SHORTAGE, newPlannedDate));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        FulfillmentEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_SCHEDULE_CHANGED);
        assertThat(saved.getStage()).isEqualTo("SHIPPING_OUT");
        assertThat(saved.getReasonType()).isEqualTo("STOCK_SHORTAGE");
        assertThat(saved.getNewPlannedDate()).isEqualTo(newPlannedDate);
    }

    @Test
    void 자동확정_알림을_아웃박스에_적재한다() {
        // when
        publisher.publishReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(1024L));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_RECEIPT_AUTO_CONFIRMED);
        assertThat(captor.getValue().getFundingId()).isEqualTo(1024L);
    }
}
