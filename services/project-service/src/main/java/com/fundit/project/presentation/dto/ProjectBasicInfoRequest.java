package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** PATCH .../basic-info — 부분 업데이트(임시저장 겸용)이므로 모든 필드가 선택값이다. */
public record ProjectBasicInfoRequest(
        @Pattern(regexp = "GENERAL|SOLE|CORP", message = "businessType은 GENERAL, SOLE, CORP 중 하나여야 합니다.")
        String businessType,
        String categoryMajor,
        String categoryMinor,
        @Size(max = 40, message = "title은 40자 이내여야 합니다.")
        String title,
        @Positive(message = "goalAmount는 양수여야 합니다.")
        Long goalAmount
) {
}
