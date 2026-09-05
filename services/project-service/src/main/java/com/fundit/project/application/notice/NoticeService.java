package com.fundit.project.application.notice;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 새소식 등록(판매자)/조회(공통), 새소식 댓글 등록/조회(PROJECT-010, PROJECT-022, PROJECT-023). */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final ProjectRepository projectRepository;
    private final ProjectNoticeJpaRepository noticeJpaRepository;
    private final ProjectNoticeCommentJpaRepository commentJpaRepository;

    @Transactional
    public ProjectNoticeJpaEntity create(UUID sellerId, UUID projectPublicId, String noticeType, String title, String content) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        return noticeJpaRepository.save(ProjectNoticeJpaEntity.builder()
                .projectId(project.getId())
                .noticeType(noticeType)
                .title(title)
                .content(content)
                .build());
    }

    /**
     * sort=POPULAR 요청도 현재는 최신순과 동일하게 처리한다 — 조회수/인기도를 집계하는 컬럼이나
     * 이벤트가 아직 없다[가정, 기획 확정 시 반영]. 기본/유일하게 지원되는 정렬은 생성일 역순이다.
     */
    @Transactional(readOnly = true)
    public Page<ProjectNoticeJpaEntity> list(UUID projectPublicId, String noticeType, Pageable pageable) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Pageable sorted = withCreatedAtDesc(pageable);
        return noticeJpaRepository.findList(project.getId(), noticeType, sorted);
    }

    @Transactional
    public ProjectNoticeCommentJpaEntity createComment(UUID memberId, Long noticeId, String content) {
        if (!noticeJpaRepository.existsById(noticeId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return commentJpaRepository.save(ProjectNoticeCommentJpaEntity.builder()
                .noticeId(noticeId)
                .memberId(memberId)
                .content(content)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<ProjectNoticeCommentJpaEntity> listComments(Long noticeId, Pageable pageable) {
        if (!noticeJpaRepository.existsById(noticeId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return commentJpaRepository.findByNoticeIdAndDeletedAtIsNull(noticeId, withCreatedAtDesc(pageable));
    }

    private Pageable withCreatedAtDesc(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
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
