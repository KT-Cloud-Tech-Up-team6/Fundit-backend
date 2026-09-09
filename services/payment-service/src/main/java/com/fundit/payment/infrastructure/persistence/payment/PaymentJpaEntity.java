package com.fundit.payment.infrastructure.persistence.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** payment.payments 매핑 전용(persistence-convention.md "복잡한 애그리거트" 4파일 구조). */
@Getter
@Entity
@Builder
@Table(name = "payments", schema = "payment")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "pg_order_id", nullable = false, length = 64)
    private String pgOrderId;

    @Column(name = "pg_payment_key", length = 200)
    private String pgPaymentKey;

    @Column(name = "pg_secret", length = 64)
    private String pgSecret;

    @Column(nullable = false)
    private long amount;

    @Column(name = "order_name", nullable = false, length = 100)
    private String orderName;

    @Column(name = "coupon_issuance_id")
    private Long couponIssuanceId;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "easy_pay_provider", length = 20)
    private String easyPayProvider;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * DB가 계산하는 생성 컬럼(GENERATED ALWAYS AS ... STORED, V1 마이그레이션 참고) — 애플리케이션이
     * 값을 쓰지 않는다(insertable/updatable false). {@code findByCompletedFundingId} 조회에만 쓰인다.
     */
    @Column(name = "completed_funding_id", insertable = false, updatable = false)
    private Long completedFundingId;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
