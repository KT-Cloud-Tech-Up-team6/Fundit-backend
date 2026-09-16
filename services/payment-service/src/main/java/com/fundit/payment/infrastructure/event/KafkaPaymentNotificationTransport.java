package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_RAISED, event.memberId().toString(), payload);
    }
}
