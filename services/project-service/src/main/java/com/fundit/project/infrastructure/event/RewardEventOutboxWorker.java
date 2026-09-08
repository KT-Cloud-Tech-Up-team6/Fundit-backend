package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link RewardEventTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다.
 */
@Component
@ConditionalOnProperty(prefix = "reward-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class RewardEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(RewardEventOutboxWorker.class);

    private final RewardEventOutboxJpaRepository outboxRepository;
    private final RewardEventTransport transport;
    private final int batchSize;

    public RewardEventOutboxWorker(RewardEventOutboxJpaRepository outboxRepository,
                                   RewardEventTransport transport,
                                   @Value("${reward-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${reward-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (RewardEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("리워드 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(RewardEventOutboxJpaEntity event) {
        if (RewardEventOutboxJpaEntity.TYPE_CREATED.equals(event.getEventType())) {
            transport.sendCreated(new RewardCreatedEvent(
                    event.getRewardId(), event.getProjectId(), event.getIsLimited(), event.getQuantity()));
            return;
        }
        if (RewardEventOutboxJpaEntity.TYPE_UPDATED.equals(event.getEventType())) {
            transport.sendUpdated(new RewardUpdatedEvent(
                    event.getRewardId(), event.getProjectId(), event.getIsLimited(), event.getQuantity()));
            return;
        }
        throw new IllegalStateException("알 수 없는 리워드 이벤트 타입: " + event.getEventType());
    }
}
