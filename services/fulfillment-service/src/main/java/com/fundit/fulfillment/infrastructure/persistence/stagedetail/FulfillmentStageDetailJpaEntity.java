package com.fundit.fulfillment.infrastructure.persistence.stagedetail;

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
 * 단순 애그리거트(persistence-convention.md 2번 — append-only, 검증/전이 로직 없음).
 * 도메인 모델·Mapper·PersistenceAdapter 없이 application 계층이 이 엔티티를 직접 다룬다.
 */
@Getter
@Entity
@Builder
@Table(name = "fulfillment_stage_details")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentStageDetailJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private Long trackerId;

    @Column(nullable = false, length = 20)
    private String stage;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "detail_text", nullable = false)
    private String detailText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }
}
