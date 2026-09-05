package com.fundit.project.application.liveverification;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** LIVE검증 콘텐츠 등록/수정/삭제(판매자), 조회(공통) — PROJECT-014, PROJECT-019. */
@Service
@RequiredArgsConstructor
public class LiveVerificationService {

    private final ProjectRepository projectRepository;
    private final LiveVerificationJpaRepository liveVerificationJpaRepository;

    @Transactional
    public LiveVerificationJpaEntity create(UUID sellerId, UUID projectPublicId, String questionSummaryId, String answer) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        return liveVerificationJpaRepository.save(LiveVerificationJpaEntity.builder()
                .projectId(project.getId())
                .questionSummaryId(questionSummaryId)
                .answer(answer)
                .build());
    }

    @Transactional
    public LiveVerificationJpaEntity update(UUID sellerId, Long id, String answer) {
        LiveVerificationJpaEntity entity = loadOwned(sellerId, id);
        entity.changeAnswer(answer);
        return liveVerificationJpaRepository.save(entity);
    }

    @Transactional
    public void delete(UUID sellerId, Long id) {
        LiveVerificationJpaEntity entity = loadOwned(sellerId, id);
        entity.softDelete();
        liveVerificationJpaRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<LiveVerificationJpaEntity> listForConsumer(UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return liveVerificationJpaRepository.findByProjectIdAndDeletedAtIsNull(project.getId());
    }

    private LiveVerificationJpaEntity loadOwned(UUID sellerId, Long id) {
        LiveVerificationJpaEntity entity = liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(entity.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return entity;
    }

    private Project loadOwnedProject(UUID sellerId, UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }
}
