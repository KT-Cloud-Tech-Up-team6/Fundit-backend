package com.fundit.fulfillment.infrastructure.persistence.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/** shipments 매핑 전용. */
@Getter
@Entity
@Builder
@Table(name = "shipments")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 레거시 컬럼 — 더 이상 애플리케이션이 쓰지 않는다(과거 데이터 조회 전용). */
    @Column(name = "funding_id", updatable = false)
    private Long fundingId;

    /** 레거시 컬럼 — 더 이상 애플리케이션이 쓰지 않는다(과거 데이터 조회 전용). */
    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(name = "funding_order_id")
    private UUID fundingOrderId;

    @Column(name = "project_public_id")
    private UUID projectPublicId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 50)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "receipt_confirmed_at")
    private Instant receiptConfirmedAt;

    @Column(name = "receipt_auto_confirmed", nullable = false)
    private boolean receiptAutoConfirmed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
