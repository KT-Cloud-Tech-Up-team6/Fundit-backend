package com.fundit.payment.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link PaymentNotificationTransport}의 실제 구현체 — Kafka {@code notification.raised.v1}로
 * 발행한다. 문구는 합리적 기본값이며 실제 프로덕트 카피는 상수만 바꾸면 된다.
 *
 * <p>relatedUrl은 임시로 내부 fundingId를 그대로 쓴다 — payment-service는 아직 order-service의
 * fundingPublicId를 받아오지 않는다(OrderFundingClient가 stub 모드, PAYMENT-001 연동 이슈로 남음).
 * 그 연동이 붙으면 이 값을 publicId로 교체해야 한다[TODO].
 */
@Component
@RequiredArgsConstructor
public class KafkaPaymentNotificationTransport implements PaymentNotificationTransport {

    private static final String SERVICE_NAME = "payment";

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendRefundStatusChanged(RefundStatusChangedEvent event, Long outboxId) {
        String title = switch (event.status()) {
            case COMPLETED -> "환불이 완료되었어요";
            case AWAITING_ALTERNATE_ACCOUNT -> "환불 처리를 위해 계좌 정보가 필요해요";
            case REJECTED -> "환불 신청이 반려되었어요";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("memberId", event.memberId());
        payload.put("notifType", "REFUND_STATUS");
        payload.put("title", title);
        payload.put("relatedUrl", "/my/fundings/" + event.fundingId() + "/refund");
        send(KafkaTopics.NOTIFICATION_RAISED, event.memberId().toString(), payload);
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
