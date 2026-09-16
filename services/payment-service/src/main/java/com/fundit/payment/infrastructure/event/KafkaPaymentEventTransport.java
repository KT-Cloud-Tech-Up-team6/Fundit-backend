package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.infrastructure.event.PaymentEventTransport.PaymentCompletedTransportEvent;
import com.fundit.payment.infrastructure.event.PaymentEventTransport.RefundCompletedTransportEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link PaymentEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 fundingId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번). order-service {@code KafkaFundingEventTransport}와
 * 동일 패턴.
 */
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventTransport implements PaymentEventTransport {

    private static final String SERVICE_NAME = "payment";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendPaymentCompleted(PaymentCompletedTransportEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("couponIssuanceId", event.couponIssuanceId());
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(event.fundingId()), payload);
    }

    @Override
    public void sendRefundCompleted(RefundCompletedTransportEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("couponIssuanceId", event.couponIssuanceId());
        payload.put("refundReason", event.refundReason());
        payload.put("fullRefund", event.fullRefund());
        kafkaTemplate.send(KafkaTopics.REFUND_COMPLETED, String.valueOf(event.fundingId()), payload);
    }
}
