package com.fundit.project.application.community;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.community.PostType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private final CommunityPostJpaRepository postJpaRepository;
    private final CommunityAnswerJpaRepository answerJpaRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final MemberPort memberPort;
    private final NotificationPort notificationPort;

    /** PROJECT-020. answered=false로 미답변 질문만 뽑아 판매자가 놓치지 않게 한다. */
    @Transactional(readOnly = true)
    public List<PostWithAnswer> list(UUID projectId, PostType postType, Boolean answered) {
        Project project = visibleProject(projectId);

        List<CommunityPostJpaEntity> posts = (postType == null)
                ? postJpaRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                : postJpaRepository.findByProjectIdAndPostTypeOrderByCreatedAtDesc(project.getId(), postType);

        Map<Long, CommunityAnswerJpaEntity> answers = answerJpaRepository
                .findByPostIdIn(posts.stream().map(CommunityPostJpaEntity::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(CommunityAnswerJpaEntity::getPostId, Function.identity()));

        Map<UUID, String> nicknames = memberPort.findNicknames(
                posts.stream().map(CommunityPostJpaEntity::getMemberId).distinct().toList());

        return posts.stream()
                .filter(post -> answered == null || answers.containsKey(post.getId()) == answered)
                .map(post -> new PostWithAnswer(post, answers.get(post.getId()), nicknames.get(post.getMemberId())))
                .toList();
    }

    /** PROJECT-021. */
    public CommunityPostJpaEntity createPost(UUID projectId, PostType postType, String content) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());

        return postJpaRepository.save(CommunityPostJpaEntity.builder()
                .projectId(project.getId())
                .memberId(currentUser.id())
                .postType(postType)
                .content(content)
                .build());
    }

    /** PROJECT-022. 질문당 답변은 1개라 이미 있으면 새로 만들지 않고 내용만 바꾼다. */
    public CommunityAnswerJpaEntity upsertAnswer(UUID projectId, Long postId, String content) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);

        CommunityPostJpaEntity post = postJpaRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!post.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        CommunityAnswerJpaEntity answer = answerJpaRepository.findByPostId(postId)
                .map(existing -> {
                    existing.changeContent(content);
                    return existing;
                })
                .orElseGet(() -> CommunityAnswerJpaEntity.builder()
                        .postId(postId)
                        .sellerId(currentUser.id())
                        .content(content)
                        .build());

        CommunityAnswerJpaEntity saved = answerJpaRepository.save(answer);
        notificationPort.notifyQuestionAnswered(postId, post.getMemberId());
        return saved;
    }

    private Project visibleProject(UUID projectId) {
        UUID viewerId = currentUserProvider.find()
                .map(CurrentUserProvider.CurrentUser::id)
                .orElse(null);
        return accessGuard.findVisible(projectId, viewerId);
    }

    public record PostWithAnswer(CommunityPostJpaEntity post, CommunityAnswerJpaEntity answer, String nickname) {
    }
}
