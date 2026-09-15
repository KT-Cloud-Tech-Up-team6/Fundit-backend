package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShippingCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FulfillmentDomainEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 fundingId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaFulfillmentDomainEventTransport implements FulfillmentDomainEventTransport {

    private static final String SERVICE_NAME = "fulfillment";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendShippingCompleted(ShippingCompletedEvent event, UUID sellerId, Instant completedAt, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("projectId", event.projectId());
        payload.put("sellerId", sellerId);
        payload.put("completedAt", completedAt);
        kafkaTemplate.send(KafkaTopics.SHIPPING_COMPLETED, String.valueOf(event.fundingId()), payload);
    }
}
