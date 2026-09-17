package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.infrastructure.persistence.event.ProjectIndexEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.ProjectIndexEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link ProjectIndexEventTransport}로 전달한다(RewardEventOutboxWorker와 동일 패턴).
 * 실패하면 published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다.
 */
@Component
@ConditionalOnProperty(prefix = "project-index-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class ProjectIndexEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(ProjectIndexEventOutboxWorker.class);

    private final ProjectIndexEventOutboxJpaRepository outboxRepository;
    private final ProjectIndexEventTransport transport;
    private final int batchSize;

    public ProjectIndexEventOutboxWorker(ProjectIndexEventOutboxJpaRepository outboxRepository,
                                         ProjectIndexEventTransport transport,
                                         @Value("${project-index-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${project-index-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (ProjectIndexEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("프로젝트 색인 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(ProjectIndexEventOutboxJpaEntity event) {
        ProjectIndexedEvent payload = new ProjectIndexedEvent(
                event.getProjectId(), event.getProjectPublicId(), event.getSellerId(), event.getSellerDisplayName(),
                event.getTitle(), event.getThumbnailUrl(), event.getCategoryMajor(), event.getCategoryMinor(),
                event.getGoalAmount(), event.getFundingStartAt(), event.getFundingDeadline(), event.getProjectCreatedAt());

        if (ProjectIndexEventOutboxJpaEntity.TYPE_APPROVED.equals(event.getEventType())) {
            transport.sendApproved(payload, event.getId());
            return;
        }
        if (ProjectIndexEventOutboxJpaEntity.TYPE_UPDATED.equals(event.getEventType())) {
            transport.sendUpdated(payload, event.getId());
            return;
        }
        throw new IllegalStateException("알 수 없는 프로젝트 색인 이벤트 타입: " + event.getEventType());
    }
}
