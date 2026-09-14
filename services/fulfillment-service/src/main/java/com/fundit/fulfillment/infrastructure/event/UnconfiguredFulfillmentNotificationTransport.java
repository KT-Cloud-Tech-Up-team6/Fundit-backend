package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import org.springframework.stereotype.Component;

/**
 * notification-service/메시지 브로커가 아직 없어 발행을 완료할 수 없다. 로깅을 성공으로 취급하지
 * 않고 예외를 던져 워커가 아웃박스 행을 미발행 상태로 재시도하게 한다
 * (order-service {@code UnconfiguredFundingEventTransport}와 동일 패턴).
 */
@Component
public class UnconfiguredFulfillmentNotificationTransport implements FulfillmentNotificationTransport {

    @Override
    public void sendStaleUpdateReminder(StaleUpdateReminderEvent event) {
        throw new IllegalStateException(
                "notification-service가 아직 구성되지 않아 StaleUpdateReminder를 발행하지 못했습니다. projectId=" + event.projectId());
    }

    @Override
    public void sendScheduleChanged(ScheduleChangedEvent event) {
        throw new IllegalStateException(
                "notification-service가 아직 구성되지 않아 ScheduleChanged를 발행하지 못했습니다. projectId=" + event.projectId());
    }

    @Override
    public void sendReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event) {
        throw new IllegalStateException(
                "notification-service가 아직 구성되지 않아 ReceiptAutoConfirmed를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }
}
