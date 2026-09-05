package com.fundit.project.application.community;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** 커뮤니티 질문/응원 등록·조회, 답변 등록/수정(UPSERT) — PROJECT-017/018/020/024/025. */
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final ProjectRepository projectRepository;
    private final CommunityPostJpaRepository postJpaRepository;
    private final CommunityAnswerJpaRepository answerJpaRepository;

    @Transactional
    public CommunityPostJpaEntity createPost(UUID memberId, UUID projectPublicId, String postType, String content) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return postJpaRepository.save(CommunityPostJpaEntity.builder()
                .projectId(project.getId())
                .memberId(memberId)
                .postType(postType)
                .content(content)
                .build());
    }

    /**
     * answeredOnly는 판매자 전용 필터다(PROJECT-017). 호출자가 이 프로젝트의 판매자가 아니면
     * (또는 비로그인이면) 필터를 조용히 무시한다 — 목록 조회 자체는 공개 API라 403으로 막지 않고,
     * 판매자 전용 파라미터만 비활성화하는 방식으로 처리한다[가정].
     */
    @Transactional(readOnly = true)
    public Page<CommunityPostView> listPosts(UUID projectPublicId, String postType, boolean answeredOnly,
                                              UUID callerId, Pageable pageable) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // 비공개 프로젝트는 본인(판매자)만 조회 가능 — 그 외에는 존재 자체를 노출하지 않는다.
        if (!project.isPublic() && !project.isOwnedBy(callerId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        boolean effectiveAnsweredOnly = answeredOnly && project.isOwnedBy(callerId);

        Page<CommunityPostJpaEntity> posts = postJpaRepository.findList(project.getId(), postType, effectiveAnsweredOnly, pageable);
        List<Long> postIds = posts.getContent().stream().map(CommunityPostJpaEntity::getId).toList();
        Map<Long, CommunityAnswerJpaEntity> answersByPostId = answerJpaRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(CommunityAnswerJpaEntity::getPostId, a -> a));

        return posts.map(post -> new CommunityPostView(post.getId(), post.getPostType(), post.getContent(),
                post.getCreatedAt(), Optional.ofNullable(answersByPostId.get(post.getId()))));
    }

    @Transactional
    public CommunityAnswerJpaEntity upsertAnswer(UUID sellerId, Long postId, String content) {
        CommunityPostJpaEntity post = postJpaRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(post.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        return answerJpaRepository.findByPostId(postId)
                .map(existing -> {
                    existing.changeContent(content);
                    return answerJpaRepository.save(existing);
                })
                .orElseGet(() -> answerJpaRepository.save(CommunityAnswerJpaEntity.builder()
                        .postId(postId).sellerId(sellerId).content(content).build()));
    }

    public record CommunityPostView(
            Long postId, String postType, String content, java.time.Instant createdAt,
            Optional<CommunityAnswerJpaEntity> answer) {
    }
}
