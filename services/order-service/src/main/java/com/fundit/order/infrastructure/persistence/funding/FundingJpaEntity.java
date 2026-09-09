package com.fundit.order.infrastructure.persistence.funding;

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
import java.util.UUID;

/**
 * fundings 루트 매핑 전용. line_items/line_item_options는 같은 애그리거트의 자식이지만
 * JPA 연관관계(@OneToMany)로 엮지 않고 별도 플랫 엔티티 + 리포지토리로 두어
 * {@link FundingPersistenceAdapter}가 수동으로 조합한다(양방향 연관관계의 복잡성을 피하기 위함 —
 * persistence-convention.md "매핑은 수동으로" 원칙과도 맞음).
 */
@Getter
@Entity
@Builder
@Table(name = "fundings")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_title", nullable = false, length = 100)
    private String projectTitle;

    @Column(name = "live_session_id")
    private Long liveSessionId;

    @Column(nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    private ShippingAddressJson shippingAddress;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "payment_expires_at", nullable = false)
    private Instant paymentExpiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
