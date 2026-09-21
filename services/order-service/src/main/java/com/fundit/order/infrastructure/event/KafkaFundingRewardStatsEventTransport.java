package com.fundit.order.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatsUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** {@link FundingRewardStatsEventTransport}의 실제 구현체 — Kafka로 발행한다. 파티션 키는 projectId다. */
@Component
@RequiredArgsConstructor
public class KafkaFundingRewardStatsEventTransport implements FundingRewardStatsEventTransport {

    private static final String SERVICE_NAME = "order";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void send(RewardStatsUpdatedEvent event, Long outboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", SERVICE_NAME + ":" + outboxId);
        payload.put("projectId", event.projectId());
        payload.put("rewardStats", event.rewardStats());

        try {
            kafkaTemplate.send(KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED, String.valueOf(event.projectId()), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
