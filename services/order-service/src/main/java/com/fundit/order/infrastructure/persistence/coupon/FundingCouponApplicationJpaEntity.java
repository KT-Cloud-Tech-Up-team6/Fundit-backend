package com.fundit.order.infrastructure.persistence.coupon;

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
 * 단순 애그리거트(persistence-convention.md 기준) — 값을 그대로 저장·조회만 하고 검증/전이
 * 로직이 없다. domain 패키지를 따로 두지 않고 application이 이 JpaRepository를 직접 쓴다.
 */
@Getter
@Entity
@Builder
@Table(name = "funding_coupon_applications")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingCouponApplicationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_id", nullable = false)
    private Long fundingId;

    @Column(name = "coupon_issuance_id", nullable = false)
    private Long couponIssuanceId;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @PrePersist
    protected void onCreate() {
        if (this.appliedAt == null) {
            this.appliedAt = Instant.now();
        }
    }
}
