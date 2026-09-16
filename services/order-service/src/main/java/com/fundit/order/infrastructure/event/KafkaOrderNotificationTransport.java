package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link OrderNotificationTransport}의 실제 구현체 — Kafka {@code notification.raised.v1}로
 * 발행한다. 문구는 합리적 기본값이며 실제 프로덕트 카피는 상수만 바꾸면 된다.
 */
@Component
@RequiredArgsConstructor
public class KafkaOrderNotificationTransport implements OrderNotificationTransport {

    private static final String SERVICE_NAME = "order";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendRewardRestocked(RewardRestockedEvent event, Long outboxId) {
        send(event.memberId(), outboxId, "REWARD_RESTOCK",
                "신청하신 리워드가 재입고되었어요",
                "/rewards/" + event.rewardId());
    }

    @Override
    public void sendCouponExpiring(CouponExpiringEvent event, Long outboxId) {
        send(event.memberId(), outboxId, "COUPON_EXPIRING",
                "보유하신 쿠폰이 곧 만료돼요",
                "/my/coupons");
    }

    private void send(UUID memberId, Long outboxId, String notifType, String title, String relatedUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("memberId", memberId);
        payload.put("notifType", notifType);
        payload.put("title", title);
        payload.put("relatedUrl", relatedUrl);
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_RAISED, memberId.toString(), payload);
    }
}
