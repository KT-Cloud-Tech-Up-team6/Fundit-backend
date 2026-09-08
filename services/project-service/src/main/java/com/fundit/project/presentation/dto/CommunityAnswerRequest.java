package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record CommunityAnswerRequest(@NotBlank String content) {
}
