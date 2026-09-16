package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FulfillmentNotificationTransport}의 실제 구현체 — Kafka {@code notification.raised.v1}로
 * 발행한다. 이 서비스가 아는 도메인 이벤트(단계·사유 등)를 여기서 "완성된 문구"로 조립한다
 * (event-convention.md 3·4번 — 알림은 단일 토픽, payload는 발행 측이 완성한 문장).
 * 문구는 합리적 기본값이며 실제 프로덕트 카피는 상수만 바꾸면 된다.
 */
@Component
@RequiredArgsConstructor
public class KafkaFulfillmentNotificationTransport implements FulfillmentNotificationTransport {

    private static final String SERVICE_NAME = "fulfillment";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendStaleUpdateReminder(StaleUpdateReminderEvent event, UUID memberId, UUID projectPublicId,
                                         Long outboxId) {
        send(memberId, outboxId, "SELLER_UPDATE_DUE",
                "제작·배송 진행현황을 1주일 넘게 갱신하지 않았어요",
                "/seller/projects/" + projectPublicId + "/fulfillment");
    }

    @Override
    public void sendScheduleChanged(ScheduleChangedEvent event, UUID memberId, UUID projectPublicId, Long outboxId) {
        // 참여자 전원 팬아웃이라 수신자별 funding publicId를 갖고 있지 않다(참가자 목록 조회는
        // memberId만 반환) — 특정 펀딩 상세가 아니라 프로젝트 기준 내 펀딩 목록으로 안내한다.
        send(memberId, outboxId, "SHIPPING_UPDATE",
                "제작·배송 일정이 변경되었어요",
                "/my/fundings?projectId=" + projectPublicId);
    }

    @Override
    public void sendReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event, UUID memberId, UUID fundingPublicId,
                                          Long outboxId) {
        send(memberId, outboxId, "SHIPPING_UPDATE",
                "수령이 자동으로 확인되었어요",
                "/my/fundings/" + fundingPublicId + "/shipping");
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
