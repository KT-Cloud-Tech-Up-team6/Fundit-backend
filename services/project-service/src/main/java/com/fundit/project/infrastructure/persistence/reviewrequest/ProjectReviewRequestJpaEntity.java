package com.fundit.project.infrastructure.persistence.reviewrequest;

import com.fundit.project.domain.project.ReviewRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Getter
@Entity
@Builder
@Table(name = "project_review_requests")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectReviewRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewRequestStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) this.submittedAt = Instant.now();
        if (this.status == null) this.status = ReviewRequestStatus.SUBMITTED;
    }

    void resolve(ReviewRequestStatus status, String rejectReason, UUID reviewerId, Instant reviewedAt) {
        this.status = status;
        this.rejectReason = rejectReason;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
    }
}
