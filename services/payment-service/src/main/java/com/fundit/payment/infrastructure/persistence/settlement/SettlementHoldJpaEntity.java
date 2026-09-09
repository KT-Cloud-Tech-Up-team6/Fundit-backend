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
 * 단순 애그리거트(persistence-convention.md §2) — 결제건별 에스크로 보류 금액. 상태 전이가
 * 단방향(HOLDING → RELEASED_*)이고 별도 비즈니스 규칙이 없어 도메인 패키지 없이 엔티티를
 * 그대로 쓴다. application이 이 리포지토리를 직접 사용한다.
 */
@Getter
@Entity
@Builder
@Table(name = "settlement_holds", schema = "settlement")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementHoldJpaEntity {

    public static final String STATUS_HOLDING = "HOLDING";
    public static final String STATUS_RELEASED_TO_SETTLEMENT = "RELEASED_TO_SETTLEMENT";
    public static final String STATUS_RELEASED_TO_REFUND = "RELEASED_TO_REFUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "hold_amount", nullable = false)
    private long holdAmount;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = STATUS_HOLDING;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void release(String newStatus) {
        this.status = newStatus;
        this.releasedAt = Instant.now();
    }

    public boolean isHolding() {
        return STATUS_HOLDING.equals(status);
    }
}
