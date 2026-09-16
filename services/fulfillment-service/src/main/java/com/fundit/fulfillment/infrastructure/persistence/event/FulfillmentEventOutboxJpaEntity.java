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
import java.util.UUID;

/**
 * 단순 애그리거트 — FULFILLMENT-004/005/010이 발행하는 알림 이벤트의 트랜잭셔널 아웃박스 행
 * (order-service {@code FundingEventOutboxJpaEntity}와 동일 패턴). 행 1개 = 수신자 1명이다 —
 * ScheduleChanged(참여자 전원 대상)는 참가자 수만큼 행을 반복 적재한다.
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

    @Column(name = "member_id")
    private UUID memberId;

    /** 이벤트 타입에 따라 프로젝트 publicId(StaleUpdateReminder) 또는 펀딩 publicId(ReceiptAutoConfirmed)가 들어간다. */
    @Column(name = "related_public_id")
    private UUID relatedPublicId;

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
