package com.fundit.search.application.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEARCH-011. project.approved.v1(최초 생성)과 project.updated.v1(갱신)을 같은 upsert 경로로
 * 처리한다 — 두 이벤트의 payload 계약이 동일하고, "색인에 없으면 새로 만든다"는 처리가 순서 역전
 * (갱신 이벤트가 승인 이벤트보다 먼저 도착)에도 그대로 맞기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class ProjectDocumentIndexSyncService implements ProjectIndexEventListener {

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Override
    @Transactional
    public void onProjectApproved(ProjectIndexedEvent event) {
        upsert(event);
    }

    @Override
    @Transactional
    public void onProjectUpdated(ProjectIndexedEvent event) {
        upsert(event);
    }

    private void upsert(ProjectIndexedEvent event) {
        projectDocumentJpaRepository.upsertProjectInfo(
                event.projectId(), event.publicId(), event.sellerId(), event.sellerDisplayName(),
                event.title(), event.thumbnailUrl(), event.categoryMajor(), event.categoryMinor(),
                event.goalAmount(), event.fundingStartAt(), event.fundingDeadline(), event.createdAt());
    }
}
