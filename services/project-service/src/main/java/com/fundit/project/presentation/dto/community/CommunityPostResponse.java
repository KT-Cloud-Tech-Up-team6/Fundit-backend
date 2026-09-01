package com.fundit.project.presentation.dto.community;

import com.fundit.project.application.community.CommunityService.PostWithAnswer;
import com.fundit.project.domain.community.PostType;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;

import java.time.Instant;

public record CommunityPostResponse(Long postId,
                                    PostType postType,
                                    String content,
                                    String memberNickname,
                                    Instant createdAt,
                                    Answer answer) {

    public record Answer(String content, Instant answeredAt) {
    }

    public static CommunityPostResponse from(PostWithAnswer source) {
        CommunityPostJpaEntity post = source.post();
        CommunityAnswerJpaEntity answer = source.answer();
        return new CommunityPostResponse(
                post.getId(),
                post.getPostType(),
                post.getContent(),
                source.nickname(),
                post.getCreatedAt(),
                answer == null ? null : new Answer(answer.getContent(), answer.getUpdatedAt()));
    }
}
