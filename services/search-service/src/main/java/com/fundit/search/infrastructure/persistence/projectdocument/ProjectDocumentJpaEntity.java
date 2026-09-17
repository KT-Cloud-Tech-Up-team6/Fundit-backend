package com.fundit.search.infrastructure.persistence.projectdocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 홈피드·카테고리탐색·검색의 "상품" 색인(SearchERD.md 2번). project-service {@code projects}의
 * 읽기 전용 비정규화 사본이라 PK도 원본 {@code projects.id}를 그대로 쓴다(별도 서로게이트 키 없음).
 *
 * <p>단순 애그리거트(persistence-convention.md §2) — 갱신은 전부 이벤트 구독(SEARCH-011~014) 또는
 * 배치(SEARCH-015)를 통해서만 일어나므로, 이 클래스에는 상태 전이 메서드를 두지 않고
 * {@code ProjectDocumentJpaRepository}의 {@code @Modifying} 쿼리로 갱신한다.
 */
@Getter
@Entity
@Builder
@Table(name = "project_documents")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDocumentJpaEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_public_id", nullable = false)
    private UUID projectPublicId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "seller_display_name", length = 50)
    private String sellerDisplayName;

    @Column(name = "title", nullable = false, length = 40)
    private String title;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "category_major", nullable = false, length = 50)
    private String categoryMajor;

    @Column(name = "category_minor", nullable = false, length = 50)
    private String categoryMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectDocumentStatus status;

    @Column(name = "goal_amount", nullable = false)
    private Long goalAmount;

    @Column(name = "funding_start_at")
    private Instant fundingStartAt;

    @Column(name = "funding_deadline", nullable = false)
    private Instant fundingDeadline;

    @Column(name = "project_created_at", nullable = false)
    private Instant projectCreatedAt;

    @Column(name = "current_amount", nullable = false)
    private Long currentAmount;

    @Column(name = "achievement_rate", nullable = false)
    private Integer achievementRate;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "funding_stats_synced_at")
    private Instant fundingStatsSyncedAt;

    @Column(name = "wish_count", nullable = false)
    private Integer wishCount;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
