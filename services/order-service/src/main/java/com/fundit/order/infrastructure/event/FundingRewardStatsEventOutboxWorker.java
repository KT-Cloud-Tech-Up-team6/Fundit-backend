package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatsUpdatedEvent;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 미발행 아웃박스 행을 꺼내 {@link FundingRewardStatsEventTransport}로 전달한다(FundingEventOutboxWorker와 동일 패턴). */
@Component
@ConditionalOnProperty(prefix = "funding-reward-stats-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FundingRewardStatsEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(FundingRewardStatsEventOutboxWorker.class);

    private final FundingRewardStatsEventOutboxJpaRepository outboxRepository;
    private final FundingRewardStatsEventTransport transport;
    private final int batchSize;

    public FundingRewardStatsEventOutboxWorker(FundingRewardStatsEventOutboxJpaRepository outboxRepository,
                                                FundingRewardStatsEventTransport transport,
                                                @Value("${funding-reward-stats-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${funding-reward-stats-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (FundingRewardStatsEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                transport.send(new RewardStatsUpdatedEvent(event.getProjectId(), event.getRewardStats()), event.getId());
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("리워드 통계 이벤트 발행 실패, 재시도 예정. id={} projectId={}", event.getId(), event.getProjectId(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }
}
