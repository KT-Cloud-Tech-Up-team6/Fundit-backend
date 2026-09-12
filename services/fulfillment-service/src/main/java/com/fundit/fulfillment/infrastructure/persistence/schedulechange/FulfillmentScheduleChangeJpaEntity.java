package com.fundit.fulfillment.infrastructure.persistence.schedulechange;

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

/** 단순 애그리거트(persistence-convention.md 2번 — append-only, 검증/전이 로직 없음). */
@Getter
@Entity
@Builder
@Table(name = "fulfillment_schedule_changes")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentScheduleChangeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private Long trackerId;

    @Column(nullable = false, length = 20)
    private String stage;

    @Column(name = "reason_type", nullable = false, length = 20)
    private String reasonType;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "old_planned_date")
    private Instant oldPlannedDate;

    @Column(name = "new_planned_date")
    private Instant newPlannedDate;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }
}
