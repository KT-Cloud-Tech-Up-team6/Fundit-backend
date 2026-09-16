package com.fundit.project.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link RewardEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 rewardId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaRewardEventTransport implements RewardEventTransport {

    private static final String SERVICE_NAME = "project";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendCreated(RewardCreatedEvent event, Long outboxId) {
        kafkaTemplate.send(KafkaTopics.REWARD_CREATED, String.valueOf(event.rewardId()),
                payload(outboxId, event.rewardId(), event.projectId(), event.isLimited(), event.quantity()));
    }

    @Override
    public void sendUpdated(RewardUpdatedEvent event, Long outboxId) {
        kafkaTemplate.send(KafkaTopics.REWARD_UPDATED, String.valueOf(event.rewardId()),
                payload(outboxId, event.rewardId(), event.projectId(), event.isLimited(), event.quantity()));
    }

    private Map<String, Object> payload(Long outboxId, Long rewardId, Long projectId,
                                         boolean isLimited, Integer quantity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("rewardId", rewardId);
        payload.put("projectId", projectId);
        payload.put("isLimited", isLimited);
        payload.put("quantity", quantity);
        return payload;
    }
}
