package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleUpdateNotificationSchedulerUnitTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentNotificationPublisher notificationPublisher;

    private StaleUpdateNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StaleUpdateNotificationScheduler(trackerRepository, notificationPublisher, 7);
    }

    @Test
    void 대상_트래커마다_미등록_알림을_발행한다() {
        // given
        FulfillmentTracker t1 = FulfillmentTracker.create(1L);
        FulfillmentTracker t2 = FulfillmentTracker.create(2L);
        when(trackerRepository.findStale(any())).thenReturn(List.of(t1, t2));

        // when
        scheduler.run();

        // then
        verify(notificationPublisher).publishStaleUpdateReminder(new StaleUpdateReminderEvent(1L));
        verify(notificationPublisher).publishStaleUpdateReminder(new StaleUpdateReminderEvent(2L));
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_처리한다() {
        // given
        FulfillmentTracker t1 = FulfillmentTracker.create(1L);
        FulfillmentTracker t2 = FulfillmentTracker.create(2L);
        when(trackerRepository.findStale(any())).thenReturn(List.of(t1, t2));
        doThrow(new RuntimeException("발행 실패")).when(notificationPublisher)
                .publishStaleUpdateReminder(new StaleUpdateReminderEvent(1L));

        // when
        scheduler.run();

        // then
        verify(notificationPublisher, times(1)).publishStaleUpdateReminder(new StaleUpdateReminderEvent(2L));
    }
}
