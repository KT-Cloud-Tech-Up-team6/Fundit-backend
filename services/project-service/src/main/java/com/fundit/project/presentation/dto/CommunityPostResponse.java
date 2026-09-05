package com.fundit.project.presentation.dto;

import java.time.Instant;

public record CommunityPostResponse(Long postId, String postType, String content, Instant createdAt) {
}
