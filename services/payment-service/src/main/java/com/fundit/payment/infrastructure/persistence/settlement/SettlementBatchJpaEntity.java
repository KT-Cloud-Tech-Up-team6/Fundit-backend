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

/** settlement.settlement_batches 매핑 전용(persistence-convention.md "복잡한 애그리거트" 4파일 구조). */
@Getter
@Entity
@Builder
@Table(name = "settlement_batches", schema = "settlement")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementBatchJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "batch_type", nullable = false, length = 10)
    private String batchType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @Column(name = "platform_fee_amount", nullable = false)
    private long platformFeeAmount;

    @Column(name = "refund_deduction_amount", nullable = false)
    private long refundDeductionAmount;

    @Column(name = "coupon_deduction_amount", nullable = false)
    private long couponDeductionAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

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
}
