package com.fundit.payment.infrastructure.persistence.payment;

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
 * 단순 애그리거트(persistence-convention.md 2번) — PG 취소 실행 이력 기록만 하고 상태 전이가
 * 없다. 도메인 패키지 없이 application 계층이 이 리포지토리를 직접 사용한다.
 */
@Getter
@Entity
@Builder
@Table(name = "payment_cancellations", schema = "payment")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "refund_request_id")
    private Long refundRequestId;

    @Column(name = "pg_transaction_key", nullable = false, length = 200)
    private String pgTransactionKey;

    @Column(name = "cancel_amount", nullable = false)
    private long cancelAmount;

    @Column(name = "cancel_reason", nullable = false, length = 200)
    private String cancelReason;

    @Column(name = "canceled_at", nullable = false)
    private Instant canceledAt;

    @PrePersist
    protected void onCreate() {
        if (this.canceledAt == null) {
            this.canceledAt = Instant.now();
        }
    }
}
