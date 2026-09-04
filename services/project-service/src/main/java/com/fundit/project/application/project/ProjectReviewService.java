package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.project.event.ProjectOpenedEvent;
import com.fundit.project.application.project.event.ProjectRejectedEvent;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ReviewDecision;
import com.fundit.project.domain.project.ReviewRequestStatus;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PROJECT-007. 검수 승인/반려. projects.status를 바꾸는 유일한 경로다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectReviewService {

    private final ProjectRepository projectRepository;
    private final ProjectReviewRequestRepository reviewRequestRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenNotifyRequestJpaRepository openNotifyRequestJpaRepository;
    private final ProjectFollowJpaRepository projectFollowJpaRepository;

    public Project review(UUID projectId,
                          ReviewDecision decision,
                          String rejectReason,
                          Instant fundingStartAt,
                          Instant fundingDeadline) {
        var currentUser = currentUserProvider.require();
        if (!currentUser.isAdmin()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        Project project = accessGuard.findOrThrow(projectId);
        Instant now = Instant.now();

        if (decision == ReviewDecision.APPROVE) {
            project.approveReview(fundingStartAt, fundingDeadline);
            Project saved = projectRepository.save(project);
            reviewRequestRepository.resolveLatest(
                    saved.getId(), ReviewRequestStatus.APPROVED, null, currentUser.id(), now);
            eventPublisher.publishEvent(new ProjectOpenedEvent(saved.getId(), openAlertTargets(saved.getId())));
            return saved;
        }

        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "반려 시에는 반려 사유가 필요합니다.");
        }
        project.rejectReview();
        Project saved = projectRepository.save(project);
        reviewRequestRepository.resolveLatest(
                saved.getId(), ReviewRequestStatus.REJECTED, rejectReason, currentUser.id(), now);
        eventPublisher.publishEvent(
                new ProjectRejectedEvent(saved.getId(), saved.getSellerId(), rejectReason));
        return saved;
    }

    /** 오픈 시점 알림 대상은 오픈알림신청자와 팔로워를 합친 집합이다. */
    private List<UUID> openAlertTargets(Long projectId) {
        List<UUID> targets = new ArrayList<>(openNotifyRequestJpaRepository.findByProjectId(projectId).stream()
                .map(OpenNotifyRequestJpaEntity::getMemberId)
                .toList());
        projectFollowJpaRepository.findByProjectId(projectId).stream()
                .map(ProjectFollowJpaEntity::getMemberId)
                .filter(memberId -> !targets.contains(memberId))
                .forEach(targets::add);
        return targets;
    }
}
