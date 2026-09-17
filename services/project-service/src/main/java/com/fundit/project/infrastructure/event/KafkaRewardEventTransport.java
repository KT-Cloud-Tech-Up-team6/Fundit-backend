package com.fundit.project.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
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
 * {@link RewardEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 rewardId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaRewardEventTransport implements RewardEventTransport {

    private static final String SERVICE_NAME = "project";

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendCreated(RewardCreatedEvent event, Long outboxId) {
        send(KafkaTopics.REWARD_CREATED, String.valueOf(event.rewardId()),
                payload(outboxId, event.rewardId(), event.projectId(), event.isLimited(), event.quantity()));
    }

    @Override
    public void sendUpdated(RewardUpdatedEvent event, Long outboxId) {
        send(KafkaTopics.REWARD_UPDATED, String.valueOf(event.rewardId()),
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
