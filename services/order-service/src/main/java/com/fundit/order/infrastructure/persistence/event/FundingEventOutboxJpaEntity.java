package com.fundit.order.infrastructure.persistence.event;

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

/** 단순 애그리거트 — 펀딩 도메인 이벤트의 트랜잭셔널 아웃박스 행(project-service 동명 클래스와 동일 패턴). */
@Getter
@Entity
@Builder
@Table(name = "funding_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingEventOutboxJpaEntity {

    public static final String TYPE_GOAL_FAILED = "FUNDING_GOAL_FAILED";
    public static final String TYPE_SUCCEEDED = "FUNDING_SUCCEEDED";
    public static final String TYPE_CANCELLED_BY_MEMBER = "FUNDING_CANCELLED_BY_MEMBER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "member_id")
    private UUID memberId;

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
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
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
