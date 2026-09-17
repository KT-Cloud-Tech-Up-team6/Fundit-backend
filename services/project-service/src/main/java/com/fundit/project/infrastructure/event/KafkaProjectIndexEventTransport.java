package com.fundit.project.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
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
 * {@link ProjectIndexEventTransport}의 실제 구현체 — Kafka로 발행한다(KafkaRewardEventTransport와 동일 패턴).
 * payload는 봉투 없이 평평한 JSON, 파티션 키는 projectId다(event-convention.md 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaProjectIndexEventTransport implements ProjectIndexEventTransport {

    private static final String SERVICE_NAME = "project";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendApproved(ProjectIndexedEvent event, Long outboxId) {
        send(KafkaTopics.PROJECT_APPROVED, String.valueOf(event.projectId()), payload(outboxId, event));
    }

    @Override
    public void sendUpdated(ProjectIndexedEvent event, Long outboxId) {
        send(KafkaTopics.PROJECT_UPDATED, String.valueOf(event.projectId()), payload(outboxId, event));
    }

    private Map<String, Object> payload(Long outboxId, ProjectIndexedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("projectId", event.projectId());
        payload.put("publicId", event.publicId());
        payload.put("sellerId", event.sellerId());
        payload.put("sellerDisplayName", event.sellerDisplayName());
        payload.put("title", event.title());
        payload.put("thumbnailUrl", event.thumbnailUrl());
        payload.put("categoryMajor", event.categoryMajor());
        payload.put("categoryMinor", event.categoryMinor());
        payload.put("goalAmount", event.goalAmount());
        payload.put("fundingStartAt", event.fundingStartAt());
        payload.put("fundingDeadline", event.fundingDeadline());
        payload.put("createdAt", event.createdAt());
        return payload;
    }

    /**
     * 전송 결과를 반드시 기다린다 — 그냥 반환하면 브로커가 죽어 있어도 워커가 성공으로 보고
     * published_at을 채운다(KafkaRewardEventTransport와 동일 이유).
     */
    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
