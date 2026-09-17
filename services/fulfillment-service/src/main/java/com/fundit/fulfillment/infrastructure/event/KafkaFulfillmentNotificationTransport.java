package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
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
 * {@link FulfillmentNotificationTransport}의 실제 구현체 — Kafka {@code notification.raised.v1}로
 * 발행한다. 이 서비스가 아는 도메인 이벤트(단계·사유 등)를 여기서 "완성된 문구"로 조립한다
 * (event-convention.md 3·4번 — 알림은 단일 토픽, payload는 발행 측이 완성한 문장).
 * 문구는 합리적 기본값이며 실제 프로덕트 카피는 상수만 바꾸면 된다.
 */
@Component
@RequiredArgsConstructor
public class KafkaFulfillmentNotificationTransport implements FulfillmentNotificationTransport {

    private static final String SERVICE_NAME = "fulfillment";

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

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
