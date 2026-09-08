package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PATCH .../story — 부분 업데이트(임시저장 겸용)이므로 모든 필드가 선택값이다. */
public record ProjectStoryRequest(
        @Size(max = 40, message = "title은 40자 이내여야 합니다.")
        String title,
        String coverImageUrl,
        @Valid
        List<IntroContentBlockRequest> introContent
) {
}
