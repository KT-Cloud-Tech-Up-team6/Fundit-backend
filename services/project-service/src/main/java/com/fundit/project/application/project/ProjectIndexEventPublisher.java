package com.fundit.project.application.project;

import java.time.Instant;
import java.util.UUID;

/**
 * 프로젝트 공개(심사 승인)/수정을 search-service 색인에 동기화하기 위한 아웃바운드 포트
 * (SEARCH-011, RewardEventPublisher와 동일 패턴). 호출부는 같은 트랜잭션에서 아웃박스에
 * 적재한다({@code OutboxProjectIndexEventPublisher}).
 */
public interface ProjectIndexEventPublisher {

    void publishProjectApproved(ProjectIndexedEvent event);

    void publishProjectUpdated(ProjectIndexedEvent event);

    /**
     * search-service {@code project_documents} 색인 계약과 동일 필드(project.approved.v1/
     * project.updated.v1 payload — event-convention.md 4번, JSON이 계약).
     *
     * @param sellerDisplayName {@link SellerProfileClient}로 조회한 스냅샷. 아직 member-service
     *                          연동 전이라 항상 null일 수 있다(SellerProfileClient 클래스 주석 참고).
     * @param thumbnailUrl      {@code Project.coverImageUrl}을 그대로 싣는다.
     */
    record ProjectIndexedEvent(
            Long projectId, UUID publicId, UUID sellerId, String sellerDisplayName, String title,
            String thumbnailUrl, String categoryMajor, String categoryMinor, Long goalAmount,
            Instant fundingStartAt, Instant fundingDeadline, Instant createdAt) {
    }
}
