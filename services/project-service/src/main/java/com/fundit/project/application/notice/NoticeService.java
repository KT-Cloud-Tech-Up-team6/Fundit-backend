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

import java.time.Instant;
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

    /** 새소식 단건 조회(본문 포함) — 열람·재편집용. 목록과 동일하게 소속 프로젝트가 공개일 때만 조회 가능. */
    @Transactional(readOnly = true)
    public ProjectNoticeJpaEntity get(Long noticeId) {
        ProjectNoticeJpaEntity notice = noticeJpaRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        projectRepository.findById(notice.getProjectId())
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return notice;
    }

    /**
     * 재편집 저장 — 소유 판매자만 가능하다. 전달되지 않은(null) 필드는 기존값을 유지한다.
     * 등록(create)과 동일하게 프로젝트 공개 여부는 따지지 않는다(소유권만 검증) — 작성 시점부터
     * 이미 그렇게 처리하고 있어 일관성을 맞춘다.
     */
    @Transactional
    public ProjectNoticeJpaEntity update(UUID sellerId, Long noticeId, String title, String content) {
        ProjectNoticeJpaEntity notice = noticeJpaRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(notice.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return noticeJpaRepository.save(ProjectNoticeJpaEntity.builder()
                .id(notice.getId())
                .projectId(notice.getProjectId())
                .noticeType(notice.getNoticeType())
                .title(title != null ? title : notice.getTitle())
                .content(content != null ? content : notice.getContent())
                .createdAt(notice.getCreatedAt())
                .updatedAt(Instant.now())
                .build());
    }

    @Transactional
    public ProjectNoticeCommentJpaEntity createComment(UUID memberId, Long noticeId, String content) {
        requirePublicNotice(noticeId);
        return commentJpaRepository.save(ProjectNoticeCommentJpaEntity.builder()
                .noticeId(noticeId)
                .memberId(memberId)
                .content(content)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<ProjectNoticeCommentJpaEntity> listComments(Long noticeId, Pageable pageable) {
        requirePublicNotice(noticeId);
        return commentJpaRepository.findByNoticeIdAndDeletedAtIsNull(noticeId, withCreatedAtDesc(pageable));
    }

    private Pageable withCreatedAtDesc(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** 공개 목록과 동일 — 새소식 없음/소속 프로젝트 비공개는 존재 여부를 구분하지 않고 404. */
    private void requirePublicNotice(Long noticeId) {
        get(noticeId);
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
