package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 프로젝트 심사 처리(승인/반려, PROJECT-030) — 관리자 전용. */
@Service
@RequiredArgsConstructor
public class ProjectReviewService {

    // [가정] PRD/API 명세서 어디에도 펀딩 기간(모금기간) 기본값이 명시돼 있지 않아 30일로 가정한다.
    // 기획에서 값이 확정되면 이 상수만 바꾸면 된다.
    private static final Duration DEFAULT_FUNDING_PERIOD = Duration.ofDays(30);

    private final ProjectRepository projectRepository;
    private final ProjectReviewRequestJpaRepository reviewRequestJpaRepository;

    @Transactional
    public Project decide(UUID adminId, UUID publicId, ReviewDecision decision, String rejectReason) {
        Project project = projectRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        ProjectReviewRequestJpaEntity reviewRequest = reviewRequestJpaRepository
                .findFirstByProjectIdAndStatusOrderBySubmittedAtDesc(project.getId(), ProjectReviewRequestJpaEntity.STATUS_SUBMITTED)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_REVIEWABLE));

        Instant now = Instant.now();
        if (decision == ReviewDecision.APPROVED) {
            project.approve(now, now.plus(DEFAULT_FUNDING_PERIOD));
            reviewRequest.approve(adminId, now);
        } else {
            if (rejectReason == null || rejectReason.isBlank()) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "반려 사유는 필수입니다.");
            }
            project.reject();
            reviewRequest.reject(adminId, now, rejectReason);
        }

        Project saved = projectRepository.save(project);
        reviewRequestJpaRepository.save(reviewRequest);
        return saved;
    }
}
