package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnconfiguredFulfillmentNotificationTransportUnitTest {

    private final UnconfiguredFulfillmentNotificationTransport transport = new UnconfiguredFulfillmentNotificationTransport();

    @Test
    void 미등록_알림_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendStaleUpdateReminder(new StaleUpdateReminderEvent(123L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StaleUpdateReminder")
                .hasMessageContaining("123");
    }

    @Test
    void 일정변경_알림_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendScheduleChanged(new ScheduleChangedEvent(123L,
                FulfillmentStage.SHIPPING_OUT, ScheduleChangeReasonType.STOCK_SHORTAGE, Instant.now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ScheduleChanged");
    }

    @Test
    void 자동확정_알림_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(1024L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ReceiptAutoConfirmed")
                .hasMessageContaining("1024");
    }
}
