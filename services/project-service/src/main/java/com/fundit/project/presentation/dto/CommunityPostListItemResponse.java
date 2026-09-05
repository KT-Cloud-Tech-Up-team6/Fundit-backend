package com.fundit.project.presentation.dto;

import java.time.Instant;

public record CommunityPostListItemResponse(
        Long postId, String postType, String content, CommunityAnswerSummary answer, Instant createdAt) {
}
