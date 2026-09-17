package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.infrastructure.persistence.event.ProjectIndexEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.ProjectIndexEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 승인/수정과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link ProjectIndexEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxProjectIndexEventPublisher implements ProjectIndexEventPublisher {

    private final ProjectIndexEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishProjectApproved(ProjectIndexedEvent event) {
        outboxRepository.save(toEntity(ProjectIndexEventOutboxJpaEntity.TYPE_APPROVED, event));
    }

    @Override
    public void publishProjectUpdated(ProjectIndexedEvent event) {
        outboxRepository.save(toEntity(ProjectIndexEventOutboxJpaEntity.TYPE_UPDATED, event));
    }

    private static ProjectIndexEventOutboxJpaEntity toEntity(String eventType, ProjectIndexedEvent event) {
        return ProjectIndexEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .projectId(event.projectId())
                .projectPublicId(event.publicId())
                .sellerId(event.sellerId())
                .sellerDisplayName(event.sellerDisplayName())
                .title(event.title())
                .thumbnailUrl(event.thumbnailUrl())
                .categoryMajor(event.categoryMajor())
                .categoryMinor(event.categoryMinor())
                .goalAmount(event.goalAmount())
                .fundingStartAt(event.fundingStartAt())
                .fundingDeadline(event.fundingDeadline())
                .projectCreatedAt(event.createdAt())
                .build();
    }
}
