package com.fundit.payment.application.notification;

import java.util.UUID;

/**
 * payment-service가 발행하는 알림 이벤트({@code notification.raised.v1})의 아웃바운드 포트.
 * 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다({@code OutboxPaymentNotificationPublisher}).
 * 실제 채널 발행은 워커가 재시도하고, {@code PaymentNotificationTransport} 구현체가 완성된
 * 문구로 조립해 보낸다(order-service {@code OrderNotificationPublisher}와 동일 패턴).
 */
public interface PaymentNotificationPublisher {

    void publishRefundStatusChanged(RefundStatusChangedEvent event);

    /** status는 {@link RefundNotificationStatus} 값 중 하나. */
    record RefundStatusChangedEvent(Long fundingId, UUID memberId, RefundNotificationStatus status) {
    }

    enum RefundNotificationStatus {
        COMPLETED,
        AWAITING_ALTERNATE_ACCOUNT,
        REJECTED
    }
}
