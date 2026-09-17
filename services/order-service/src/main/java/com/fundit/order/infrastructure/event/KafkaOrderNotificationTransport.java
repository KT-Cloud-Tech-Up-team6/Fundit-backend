package com.fundit.order.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link OrderNotificationTransport}의 실제 구현체 — Kafka {@code notification.raised.v1}로
 * 발행한다. 문구는 합리적 기본값이며 실제 프로덕트 카피는 상수만 바꾸면 된다.
 */
@Component
@RequiredArgsConstructor
public class KafkaOrderNotificationTransport implements OrderNotificationTransport {

    private static final String SERVICE_NAME = "order";

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

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
        send(KafkaTopics.NOTIFICATION_RAISED, memberId.toString(), payload);
    }

    /**
     * <b>전송 결과를 반드시 기다린다.</b> {@code send()}는 결과를 미래에 채우는 비동기 호출이라
     * 그냥 반환하면 브로커가 죽어 있어도 워커가 성공으로 보고 {@code published_at}을 채운다 —
     * 행이 발행된 척 사라지고 아웃박스를 둔 이유가 통째로 무력화된다.
     *
     * <p>실패는 {@link DependencyFailureException}으로 감싸 워커가 미발행으로 남기게 한다
     * (error-handling.md: 외부 연동 실패는 infrastructure 계층에서 감싼다).
     */
    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 상위(스케줄러 종료 등)가 중단 신호를 영영 못 본다.
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
