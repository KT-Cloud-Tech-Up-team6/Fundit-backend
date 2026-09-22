package com.fundit.member.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 찜 목록 표시용 프로젝트 스냅샷 동기화 — project-service {@code project.approved.v1}/{@code project.updated.v1}.
 * 두 토픽은 payload 계약이 같다. 공개 전 프로젝트는 애초에 발행되지 않으므로 받는 대로 반영한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectSnapshotKafkaListener {

    private final ProjectSnapshotJpaRepository projectSnapshotJpaRepository;

    @KafkaListener(topics = {KafkaTopics.PROJECT_APPROVED, KafkaTopics.PROJECT_UPDATED})
    public void onProjectChanged(ProjectChangedEvent event) {
        projectSnapshotJpaRepository.upsert(event.projectId(), event.publicId(), event.title(),
                event.thumbnailUrl(), event.sourceVersion());
    }

    /** 발행 측 {@code ProjectIndexedEvent} 중 찜 목록에 필요한 필드만 받는다(JSON이 계약). */
    public record ProjectChangedEvent(Long projectId, UUID publicId, String title, String thumbnailUrl,
                                      Long sourceVersion) {
    }
}
