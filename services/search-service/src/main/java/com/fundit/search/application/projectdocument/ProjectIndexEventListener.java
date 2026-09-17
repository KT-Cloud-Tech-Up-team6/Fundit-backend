package com.fundit.search.application.projectdocument;

import java.time.Instant;
import java.util.UUID;

/**
 * SEARCH-011 인바운드 포트 — project-service {@code project.approved.v1}/{@code project.updated.v1}를
 * 구독해 검색 색인을 만든다. DRAFT/PENDING_REVIEW는 비공개라 project-service가 애초에 이 이벤트를
 * 보내지 않으므로, 여기 도착하는 프로젝트는 전부 색인 대상이다.
 */
public interface ProjectIndexEventListener {

    void onProjectApproved(ProjectIndexedEvent event);

    void onProjectUpdated(ProjectIndexedEvent event);

    /** project-service {@code ProjectIndexEventPublisher.ProjectIndexedEvent}와 동일 계약(JSON이 계약). */
    record ProjectIndexedEvent(
            Long projectId, UUID publicId, UUID sellerId, String sellerDisplayName, String title,
            String thumbnailUrl, String categoryMajor, String categoryMinor, Long goalAmount,
            Instant fundingStartAt, Instant fundingDeadline, Instant createdAt) {
    }
}
