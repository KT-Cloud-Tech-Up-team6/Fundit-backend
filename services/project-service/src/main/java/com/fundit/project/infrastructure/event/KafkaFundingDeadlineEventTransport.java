package com.fundit.project.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.project.FundingDeadlinePublisher.FundingDeadlineReachedEvent;
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
 * {@link FundingDeadlineEventTransport}의 실제 구현체 — Kafka로 발행한다.
 * payload는 봉투 없이 평평한 JSON(record 필드 + eventId), 파티션 키는 projectId다
 * (`.claude/rules/event-convention.md` 1·2·4·5번).
 */
@Component
@RequiredArgsConstructor
public class KafkaFundingDeadlineEventTransport implements FundingDeadlineEventTransport {

    private static final String SERVICE_NAME = "project";

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void send(FundingDeadlineReachedEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("projectId", event.projectId());
        payload.put("goalAmount", event.goalAmount());

        try {
            kafkaTemplate.send(KafkaTopics.PROJECT_FUNDING_DEADLINE_REACHED, String.valueOf(event.projectId()), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 상위(스케줄러 종료 등)가 중단 신호를 영영 못 본다.
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
