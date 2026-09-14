package com.fundit.fulfillment.infrastructure.persistence.event;

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

/**
 * 단순 애그리거트 — FULFILLMENT-004/005/010이 발행하는 알림 이벤트의 트랜잭셔널 아웃박스 행
 * (order-service {@code FundingEventOutboxJpaEntity}와 동일 패턴). notification-service가
 * 아직 없고 메시지 브로커도 미확정이라 이 패턴을 그대로 따른다.
 */
@Getter
@Entity
@Builder
@Table(name = "fulfillment_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentEventOutboxJpaEntity {

    public static final String TYPE_STALE_UPDATE_REMINDER = "STALE_UPDATE_REMINDER";
    public static final String TYPE_SCHEDULE_CHANGED = "SCHEDULE_CHANGED";
    public static final String TYPE_RECEIPT_AUTO_CONFIRMED = "RECEIPT_AUTO_CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "funding_id")
    private Long fundingId;

    @Column(length = 20)
    private String stage;

    @Column(name = "reason_type", length = 20)
    private String reasonType;

    @Column(name = "new_planned_date")
    private Instant newPlannedDate;

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
