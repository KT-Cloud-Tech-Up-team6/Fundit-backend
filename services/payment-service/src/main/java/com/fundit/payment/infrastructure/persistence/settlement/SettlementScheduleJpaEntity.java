package com.fundit.payment.infrastructure.persistence.settlement;

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
 * 단순 애그리거트(persistence-convention.md §2) — PAYMENT-013/014 실행 대상 등록용 내부 큐
 * (V1 마이그레이션 코멘트 참고, ERD에 없던 신규 테이블).
 */
@Getter
@Entity
@Builder
@Table(name = "settlement_schedule", schema = "settlement")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementScheduleJpaEntity {

    public static final String TYPE_INTERIM = "INTERIM";
    public static final String TYPE_FINAL = "FINAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "batch_type", nullable = false, length = 10)
    private String batchType;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
    }
}
