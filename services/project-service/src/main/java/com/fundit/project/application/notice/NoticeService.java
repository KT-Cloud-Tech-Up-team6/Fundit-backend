package com.fundit.project.application.notice;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.notice.NoticeType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeService {

    private static final String SORT_POPULAR = "POPULAR";
    private static final int MAX_COMMENT_LENGTH = 500;

    private final ProjectNoticeJpaRepository noticeJpaRepository;
    private final ProjectNoticeCommentJpaRepository commentJpaRepository;
    private final ProjectFollowJpaRepository followJpaRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final MemberPort memberPort;
    private final NotificationPort notificationPort;

    /** PROJECT-016. 등록되면 팔로워 전원에게 알림을 보낸다. */
    public ProjectNoticeJpaEntity create(UUID projectId, String noticeType, String title, String content) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);

        if (!NoticeType.isSupported(noticeType)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 새소식 유형입니다: " + noticeType);
        }

        ProjectNoticeJpaEntity saved = noticeJpaRepository.save(ProjectNoticeJpaEntity.builder()
                .projectId(project.getId())
                .noticeType(noticeType)
                .title(title)
                .content(content)
                .build());

        notificationPort.notifyNoticePublished(project.getId(), saved.getId(),
                followJpaRepository.findByProjectId(project.getId()).stream()
                        .map(ProjectFollowJpaEntity::getMemberId)
                        .toList());
        return saved;
    }

    /**
     * PROJECT-017. 기본 정렬은 최신순.
     * POPULAR는 별도 지표 컬럼이 없어 댓글 수로 대신한다 — 한 프로젝트의 새소식 건수가 많지 않아
     * 메모리에서 정렬한다. 조회수 등 실제 인기 지표가 생기면 쿼리로 옮길 것.
     */
    @Transactional(readOnly = true)
    public List<NoticeSummary> list(UUID projectId, String noticeType, String sort) {
        Project project = visibleProject(projectId);

        List<ProjectNoticeJpaEntity> notices = (noticeType == null || noticeType.isBlank())
                ? noticeJpaRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                : noticeJpaRepository.findByProjectIdAndNoticeTypeOrderByCreatedAtDesc(project.getId(), noticeType);

        List<NoticeSummary> summaries = notices.stream()
                .map(notice -> new NoticeSummary(notice,
                        commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(notice.getId()).size()))
                .toList();

        if (SORT_POPULAR.equalsIgnoreCase(sort)) {
            return summaries.stream()
                    .sorted(Comparator.comparingInt(NoticeSummary::commentCount).reversed())
                    .toList();
        }
        return summaries;
    }

    /** PROJECT-018. 로그인 회원이면 누구나 작성할 수 있다. */
    public ProjectNoticeCommentJpaEntity addComment(UUID projectId, Long noticeId, String content) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());
        ProjectNoticeJpaEntity notice = findNoticeOf(project, noticeId);

        if (content == null || content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "댓글은 %d자 이내여야 합니다.".formatted(MAX_COMMENT_LENGTH));
        }

        return commentJpaRepository.save(ProjectNoticeCommentJpaEntity.builder()
                .noticeId(notice.getId())
                .memberId(currentUser.id())
                .content(content)
                .build());
    }

    /** PROJECT-019. 삭제된 댓글은 제외하고 최신순으로 돌려준다. */
    @Transactional(readOnly = true)
    public List<CommentWithAuthor> listComments(UUID projectId, Long noticeId) {
        Project project = visibleProject(projectId);
        ProjectNoticeJpaEntity notice = findNoticeOf(project, noticeId);

        List<ProjectNoticeCommentJpaEntity> comments =
                commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(notice.getId());
        Map<UUID, String> nicknames = memberPort.findNicknames(
                comments.stream().map(ProjectNoticeCommentJpaEntity::getMemberId).distinct().toList());

        return comments.stream()
                .map(comment -> new CommentWithAuthor(comment, nicknames.get(comment.getMemberId())))
                .toList();
    }

    private ProjectNoticeJpaEntity findNoticeOf(Project project, Long noticeId) {
        ProjectNoticeJpaEntity notice = noticeJpaRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!notice.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return notice;
    }

    private Project visibleProject(UUID projectId) {
        UUID viewerId = currentUserProvider.find()
                .map(CurrentUserProvider.CurrentUser::id)
                .orElse(null);
        return accessGuard.findVisible(projectId, viewerId);
    }

    public record NoticeSummary(ProjectNoticeJpaEntity notice, int commentCount) {
    }

    public record CommentWithAuthor(ProjectNoticeCommentJpaEntity comment, String nickname) {
    }
}
