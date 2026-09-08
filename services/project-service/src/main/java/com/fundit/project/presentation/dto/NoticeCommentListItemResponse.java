package com.fundit.project.presentation.dto;

import java.time.Instant;

public record NoticeCommentListItemResponse(Long commentId, String content, Instant createdAt) {
}
