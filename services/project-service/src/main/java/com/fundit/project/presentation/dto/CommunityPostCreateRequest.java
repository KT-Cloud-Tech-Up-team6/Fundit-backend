package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CommunityPostCreateRequest(
        @NotBlank
        @Pattern(regexp = "QUESTION|CHEER", message = "postType은 QUESTION 또는 CHEER여야 합니다.")
        String postType,
        @NotBlank String content
) {
}
