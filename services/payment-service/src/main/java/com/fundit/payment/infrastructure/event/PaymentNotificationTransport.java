package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;

/**
 * 아웃박스에 적재된 알림 이벤트를 실제 채널({@code notification.raised.v1})로 보내는 전송 포트.
 */
public interface PaymentNotificationTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("payment:{outboxId}")의 재료다(event-convention.md 5번). */
    void sendRefundStatusChanged(RefundStatusChangedEvent event, Long outboxId);
}
