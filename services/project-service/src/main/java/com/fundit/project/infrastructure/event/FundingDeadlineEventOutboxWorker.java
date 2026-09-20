package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher.FundingDeadlineReachedEvent;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link FundingDeadlineEventTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다.
 */
@Component
@ConditionalOnProperty(prefix = "funding-deadline-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FundingDeadlineEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(FundingDeadlineEventOutboxWorker.class);

    private final FundingDeadlineEventOutboxJpaRepository outboxRepository;
    private final FundingDeadlineEventTransport transport;
    private final int batchSize;

    public FundingDeadlineEventOutboxWorker(FundingDeadlineEventOutboxJpaRepository outboxRepository,
                                             FundingDeadlineEventTransport transport,
                                             @Value("${funding-deadline-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${funding-deadline-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (FundingDeadlineEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                transport.send(new FundingDeadlineReachedEvent(event.getProjectId(), event.getGoalAmount()), event.getId());
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("펀딩 마감 이벤트 발행 실패, 재시도 예정. id={} projectId={}", event.getId(), event.getProjectId(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }
}
