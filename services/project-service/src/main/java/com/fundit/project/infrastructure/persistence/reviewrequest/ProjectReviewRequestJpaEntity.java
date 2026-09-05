package com.fundit.project.infrastructure.persistence.reviewrequest;

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
 * 단순 애그리거트(persistence-convention.md §2) — append 성격의 심사 이력 로그.
 * 실제 상태전이 불변식(PENDING_REVIEW인지 등)은 {@code Project} 도메인이 갖고 있고,
 * 이 테이블은 그 결과를 기록만 하므로 domain/Mapper/Adapter 없이 application이 직접 사용한다.
 */
@Getter
@Entity
@Builder
@Table(name = "project_review_requests")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectReviewRequestJpaEntity {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) this.submittedAt = Instant.now();
        if (this.status == null) this.status = STATUS_SUBMITTED;
    }

    /** 심사 승인 처리 — 관리자/처리시각을 기록한다(PROJECT-030). */
    public void approve(UUID reviewerId, Instant reviewedAt) {
        this.status = STATUS_APPROVED;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    /** 심사 반려 처리 — 사유는 필수(컨트롤러/서비스에서 이미 검증됨). */
    public void reject(UUID reviewerId, Instant reviewedAt, String rejectReason) {
        this.status = STATUS_REJECTED;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
    }
}
