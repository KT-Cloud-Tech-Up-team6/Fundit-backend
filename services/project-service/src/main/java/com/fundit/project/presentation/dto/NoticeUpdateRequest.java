package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.Size;

/** PATCH .../notices/{noticeId} — 전달된 필드만 갱신한다(null이면 기존값 유지, RewardUpdateRequest와 동일 패턴). */
public record NoticeUpdateRequest(
        @Size(max = 100) String title,
        String content
) {
}
