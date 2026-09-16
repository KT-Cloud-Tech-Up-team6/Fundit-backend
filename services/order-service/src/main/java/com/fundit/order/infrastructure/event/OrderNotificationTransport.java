package com.fundit.order.infrastructure.event;

import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;

/**
 * 아웃박스에 적재된 알림 이벤트를 실제 채널({@code notification.raised.v1})로 보내는 전송 포트.
 */
public interface OrderNotificationTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("order:{outboxId}")의 재료다(event-convention.md 5번). */
    void sendRewardRestocked(RewardRestockedEvent event, Long outboxId);

    void sendCouponExpiring(CouponExpiringEvent event, Long outboxId);
}
