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
import java.util.UUID;

/**
 * 쿠폰 템플릿 JPA 매핑 전용. 다른 서비스 관례(예: project-service ProjectJpaEntity)와 동일하게
 * enum은 String 컬럼으로 저장하고 Mapper에서 valueOf()/name()으로 변환한다.
 * remaining_quantity/used_budget_amount 증감은 이 엔티티를 로드→수정→save()하지 않고
 * {@link CouponJpaRepository}의 조건부 @Modifying 쿼리로만 한다(재고와 동일 원칙).
 */
@Getter
@Entity
@Builder
@Table(name = "coupons")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_code", nullable = false, length = 30)
    private String couponCode;

    @Column(name = "coupon_name", nullable = false, length = 100)
    private String couponName;

    @Column(name = "discount_type", nullable = false, length = 15)
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "budget_limit")
    private Long budgetLimit;

    @Column(name = "used_budget_amount", nullable = false)
    private long usedBudgetAmount;

    @Column(name = "issuer_type", nullable = false, length = 10)
    private String issuerType;

    @Column(name = "issuer_id")
    private UUID issuerId;

    @Column(name = "target_scope", nullable = false, length = 20)
    private String targetScope;

    @Column(name = "target_ref_id", length = 50)
    private String targetRefId;

    @Column(name = "min_funding_amount", nullable = false)
    private long minFundingAmount;

    @Column(name = "per_member_limit", nullable = false)
    private int perMemberLimit;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "issue_channel", nullable = false, length = 10)
    private String issueChannel;

    @Column(name = "live_session_id")
    private Long liveSessionId;

    @Column(name = "drop_type", length = 20)
    private String dropType;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
