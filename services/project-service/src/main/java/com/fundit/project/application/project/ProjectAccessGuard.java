package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 프로젝트 조회 + 접근 판정을 한곳에 모은다.
 * 판매자 전용 API는 소유자가 아니면 403, 공개 조회는 비공개 프로젝트의 존재 자체를 숨기려고 404를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final ProjectRepository projectRepository;

    public Project findOrThrow(UUID publicId) {
        return projectRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 판매자 본인만 접근 가능한 API용. */
    public Project findOwned(UUID publicId, CurrentUser currentUser) {
        Project project = findOrThrow(publicId);
        if (!project.isOwnedBy(currentUser.id())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }

    /**
     * 공개 조회용. DRAFT/PENDING_REVIEW 프로젝트를 소유자가 아닌 사용자가 조회하면
     * 403이 아니라 404로 응답해 존재 여부를 노출하지 않는다.
     */
    public Project findVisible(UUID publicId, UUID viewerId) {
        Project project = findOrThrow(publicId);
        if (!project.isVisibleTo(viewerId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return project;
    }
}
