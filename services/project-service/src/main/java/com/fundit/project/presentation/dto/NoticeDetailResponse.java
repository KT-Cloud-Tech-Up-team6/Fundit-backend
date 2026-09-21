package com.fundit.project.presentation.dto;

import java.time.Instant;

/** 새소식 단건 조회(본문 포함) — 열람·재편집용. */
public record NoticeDetailResponse(Long noticeId, String noticeType, String title, String content, Instant createdAt) {
}
