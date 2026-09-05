package com.fundit.project.presentation.dto;

import java.time.Instant;

public record NoticeResponse(Long noticeId, String noticeType, String title, Instant createdAt) {
}
