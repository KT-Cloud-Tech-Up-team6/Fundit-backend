package com.fundit.project.presentation.dto;

import java.time.Instant;

public record NoticeCommentResponse(Long commentId, Long noticeId, String content, Instant createdAt) {
}
