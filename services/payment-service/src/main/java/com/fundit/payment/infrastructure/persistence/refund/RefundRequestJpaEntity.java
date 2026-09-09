package com.fundit.payment.infrastructure.persistence.refund;

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
import java.util.List;
import java.util.UUID;

/** refund.refund_requests 매핑 전용(persistence-convention.md "복잡한 애그리거트" 4파일 구조). */
@Getter
@Entity
@Builder
@Table(name = "refund_requests", schema = "refund")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "is_full_refund")
    private Boolean isFullRefund;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_urls", columnDefinition = "jsonb")
    private List<String> evidenceUrls;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    /** 암호화된 문자열(AesGcmCipher). 평문 변환은 {@code RefundRequestMapper}가 담당한다. */
    @Column(name = "alternate_refund_account")
    private String alternateRefundAccountCipherText;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = Instant.now();
        }
    }
}
