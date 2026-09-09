package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link FundingEventTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (project-service RewardEventOutboxWorker와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "funding-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FundingEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(FundingEventOutboxWorker.class);

    private final FundingEventOutboxJpaRepository outboxRepository;
    private final FundingEventTransport transport;
    private final int batchSize;

    public FundingEventOutboxWorker(FundingEventOutboxJpaRepository outboxRepository,
                                     FundingEventTransport transport,
                                     @Value("${funding-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${funding-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (FundingEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("펀딩 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(FundingEventOutboxJpaEntity event) {
        switch (event.getEventType()) {
            case FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED ->
                    transport.sendGoalFailed(new FundingGoalFailedEvent(event.getFundingId(), event.getProjectId()));
            case FundingEventOutboxJpaEntity.TYPE_SUCCEEDED ->
                    transport.sendSucceeded(new FundingSucceededEvent(event.getFundingId(), event.getProjectId()));
            case FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER -> transport.sendCancelledByMember(
                    new FundingCancelledByMemberEvent(event.getFundingId(), event.getProjectId(), event.getMemberId()));
            default -> throw new IllegalStateException("알 수 없는 펀딩 이벤트 타입: " + event.getEventType());
        }
    }
}
