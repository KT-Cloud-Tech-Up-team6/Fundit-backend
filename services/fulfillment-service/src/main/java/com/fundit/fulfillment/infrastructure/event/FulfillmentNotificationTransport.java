package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;

/**
 * 아웃박스에 적재된 알림 이벤트를 실제 채널(notification-service)로 보내는 전송 포트.
 * notification-service/브로커가 확정되면 이 인터페이스의 구현체만 교체한다
 * (order-service {@code FundingEventTransport}와 동일 패턴).
 */
public interface FulfillmentNotificationTransport {

    void sendStaleUpdateReminder(StaleUpdateReminderEvent event);

    void sendScheduleChanged(ScheduleChangedEvent event);

    void sendReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event);
}
