package com.fundit.fulfillment.infrastructure.persistence.tracker;

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

/** fulfillment_trackers 매핑 전용. */
@Getter
@Entity
@Builder
@Table(name = "fulfillment_trackers")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentTrackerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 레거시 컬럼 — 더 이상 애플리케이션이 쓰지 않는다(과거 데이터 조회 전용). */
    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(name = "project_public_id")
    private UUID projectPublicId;

    @Column(name = "current_stage", nullable = false, length = 20)
    private String currentStage;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
