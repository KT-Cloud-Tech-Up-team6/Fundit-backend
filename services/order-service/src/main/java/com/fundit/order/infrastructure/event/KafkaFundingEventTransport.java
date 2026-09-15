package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FundingEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 fundingId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaFundingEventTransport implements FundingEventTransport {

    private static final String SERVICE_NAME = "order";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendGoalFailed(FundingGoalFailedEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("projectId", event.projectId());
        kafkaTemplate.send(KafkaTopics.FUNDING_GOAL_FAILED, String.valueOf(event.fundingId()), payload);
    }

    @Override
    public void sendSucceeded(FundingSucceededEvent event, UUID sellerId, Instant achievedAt, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("projectId", event.projectId());
        payload.put("sellerId", sellerId);
        payload.put("achievedAt", achievedAt);
        kafkaTemplate.send(KafkaTopics.FUNDING_SUCCEEDED, String.valueOf(event.fundingId()), payload);
    }

    @Override
    public void sendCancelledByMember(FundingCancelledByMemberEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("fundingId", event.fundingId());
        payload.put("projectId", event.projectId());
        payload.put("memberId", event.memberId());
        kafkaTemplate.send(KafkaTopics.FUNDING_CANCELLED_BY_MEMBER, String.valueOf(event.fundingId()), payload);
    }
}
