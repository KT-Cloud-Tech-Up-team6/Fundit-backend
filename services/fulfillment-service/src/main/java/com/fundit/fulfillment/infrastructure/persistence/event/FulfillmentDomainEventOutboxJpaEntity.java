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

/** 단순 애그리거트 — shipping.completed.v1 등 도메인 이벤트의 트랜잭셔널 아웃박스 행. */
@Getter
@Entity
@Builder
@Table(name = "fulfillment_domain_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentDomainEventOutboxJpaEntity {

    public static final String TYPE_SHIPPING_COMPLETED = "SHIPPING_COMPLETED";
    public static final String TYPE_SHIPMENT_SHIPPED = "SHIPMENT_SHIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 레거시 컬럼 — 더 이상 애플리케이션이 쓰지 않는다. */
    @Column(name = "funding_id", updatable = false)
    private Long fundingId;

    /** 레거시 컬럼 — 더 이상 애플리케이션이 쓰지 않는다. */
    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(name = "funding_order_id")
    private UUID fundingOrderId;

    @Column(name = "project_public_id")
    private UUID projectPublicId;

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
