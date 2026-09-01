package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectCommandService {

    private final ProjectRepository projectRepository;
    private final ProjectReviewRequestRepository reviewRequestRepository;
    private final RewardRepository rewardRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;

    /** PROJECT-001. 입력값 없이 로그인 사용자를 seller_id로 하는 DRAFT 행을 만든다. */
    public Project create() {
        var currentUser = currentUserProvider.require();
        return projectRepository.save(Project.createDraft(currentUser.id()));
    }

    /** PROJECT-004. */
    public Project updateBasicInfo(UUID projectId,
                                   BusinessType businessType,
                                   String categoryMajor,
                                   String categoryMinor,
                                   Long goalAmount,
                                   boolean privacyAgreed) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);

        if (!categoryJpaRepository.existsByIdCategoryMajorAndIdCategoryMinor(categoryMajor, categoryMinor)) {
            throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY);
        }

        project.updateBasicInfo(businessType, categoryMajor, categoryMinor, goalAmount, privacyAgreed);
        return projectRepository.save(project);
    }

    /**
     * PROJECT-005. isDraft=true면 임시저장, false면 검수 요청까지 진행한다.
     * 검수 요청은 리워드 1개 이상을 요구하는데 리워드는 별도 애그리거트라 여기서 조회해 넘긴다.
     */
    public Project updateDetail(UUID projectId,
                                String title,
                                String thumbnailImageUrl,
                                Map<String, Object> introContent,
                                boolean draft) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);

        if (draft) {
            project.updateDetail(title, thumbnailImageUrl, introContent);
            return projectRepository.save(project);
        }

        // 이미 PENDING_REVIEW면 중복 검수요청이라 409로 응답해야 한다. updateDetail()을 먼저 태우면
        // 수정 잠금 규칙에 걸려 423이 나가버리므로, 상태 확인을 앞에 둔다.
        if (project.getStatus() == ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 검수 요청된 프로젝트입니다.");
        }

        boolean hasReward = rewardRepository.existsActiveByProjectId(project.getId());
        project.updateDetail(title, thumbnailImageUrl, introContent);
        project.submitForReview(hasReward);

        Project saved = projectRepository.save(project);
        reviewRequestRepository.submit(saved.getId(), Instant.now());
        return saved;
    }

    /** PROJECT-006. 소프트 삭제. 검수 중·진행 중 프로젝트는 422로 막는다. */
    public void delete(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);
        project.softDelete(Instant.now());
        projectRepository.save(project);
    }
}
