package com.fundit.project.infrastructure.persistence.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 단순 애그리거트 — 프로젝트 색인 이벤트(SEARCH-011)의 트랜잭셔널 아웃박스 행. */
@Getter
@Entity
@Builder
@Table(name = "project_index_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectIndexEventOutboxJpaEntity {

    public static final String TYPE_APPROVED = "PROJECT_APPROVED";
    public static final String TYPE_UPDATED = "PROJECT_UPDATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "project_id", nullable = false)
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

    @Column(name = "goal_amount", nullable = false)
    private Long goalAmount;

    @Column(name = "funding_start_at")
    private Instant fundingStartAt;

    @Column(name = "funding_deadline", nullable = false)
    private Instant fundingDeadline;

    @Column(name = "project_created_at", nullable = false)
    private Instant projectCreatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attemptCount += 1;
        this.lastError = error;
    }
}
