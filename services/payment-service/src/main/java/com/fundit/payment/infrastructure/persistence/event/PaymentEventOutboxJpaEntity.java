package com.fundit.payment.infrastructure.persistence.event;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 단순 애그리거트 — 결제/환불 완료 이벤트의 트랜잭셔널 아웃박스 행(PAYMENT-016).
 * order-service {@code FundingEventOutboxJpaEntity}와 동일 패턴이며, payload만 JSONB로 둔다
 * (이벤트 타입별로 필드 구성이 달라 컬럼을 고정하지 않기 위함, PaymentERD.md 3장).
 */
@Getter
@Entity
@Builder
@Table(name = "payment_event_outbox", schema = "payment")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventOutboxJpaEntity {

    public static final String TYPE_PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String TYPE_REFUND_COMPLETED = "RefundCompleted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

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
