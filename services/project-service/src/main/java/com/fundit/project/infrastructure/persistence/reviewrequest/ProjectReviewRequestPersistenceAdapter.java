package com.fundit.project.infrastructure.persistence.reviewrequest;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ReviewRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectReviewRequestPersistenceAdapter implements ProjectReviewRequestRepository {

    private final ProjectReviewRequestJpaRepository jpaRepository;

    @Override
    public void submit(Long projectId, Instant submittedAt) {
        jpaRepository.save(ProjectReviewRequestJpaEntity.builder()
                .projectId(projectId)
                .status(ReviewRequestStatus.SUBMITTED)
                .submittedAt(submittedAt)
                .build());
    }

    @Override
    public void resolveLatest(Long projectId,
                              ReviewRequestStatus status,
                              String rejectReason,
                              UUID reviewerId,
                              Instant reviewedAt) {
        ProjectReviewRequestJpaEntity latest = jpaRepository
                .findFirstByProjectIdOrderBySubmittedAtDesc(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING));
        latest.resolve(status, rejectReason, reviewerId, reviewedAt);
        jpaRepository.save(latest);
    }
}
