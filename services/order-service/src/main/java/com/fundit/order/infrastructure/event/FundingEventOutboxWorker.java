package com.fundit.order.infrastructure.event;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
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

import java.util.UUID;

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
    private final ProjectOwnershipClient projectOwnershipClient;
    private final int batchSize;

    public FundingEventOutboxWorker(FundingEventOutboxJpaRepository outboxRepository,
                                     FundingEventTransport transport,
                                     ProjectOwnershipClient projectOwnershipClient,
                                     @Value("${funding-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.projectOwnershipClient = projectOwnershipClient;
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
            case FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED -> transport.sendGoalFailed(
                    new FundingGoalFailedEvent(event.getFundingId(), event.getProjectId()), event.getId());
            case FundingEventOutboxJpaEntity.TYPE_SUCCEEDED -> {
                // payment-service FundingSucceededListener가 기대하는 필드 추가분(FundingEventTransport
                // 참고) — achievedAt은 이 아웃박스 행이 만들어진 시각(=목표달성 판정 시각)을 그대로 쓴다.
                UUID sellerId = projectOwnershipClient.findSellerId(event.getProjectId())
                        .orElseThrow(() -> new IllegalStateException(
                                "FundingSucceeded 발행 대상 프로젝트의 sellerId를 찾을 수 없습니다. projectId=" + event.getProjectId()));
                transport.sendSucceeded(new FundingSucceededEvent(event.getFundingId(), event.getProjectId()),
                        sellerId, event.getCreatedAt(), event.getId());
            }
            case FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER -> transport.sendCancelledByMember(
                    new FundingCancelledByMemberEvent(event.getFundingId(), event.getProjectId(), event.getMemberId()),
                    event.getId());
            default -> throw new IllegalStateException("알 수 없는 펀딩 이벤트 타입: " + event.getEventType());
        }
    }
}
