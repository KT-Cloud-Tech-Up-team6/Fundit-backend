package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IntroContentBlockRequest(
        @NotBlank
        @Pattern(regexp = "TEXT|IMAGE|VIDEO_URL", message = "type은 TEXT, IMAGE, VIDEO_URL 중 하나여야 합니다.")
        String type,
        @NotBlank
        String value
) {
}
