package com.fundit.member.presentation.dto;

import java.time.Instant;

public record WishListItemResponse(Long projectId, String projectTitle, String projectThumbnailUrl, Instant createdAt) {
}
