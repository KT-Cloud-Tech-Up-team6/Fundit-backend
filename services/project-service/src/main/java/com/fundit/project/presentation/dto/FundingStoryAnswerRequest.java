package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record FundingStoryAnswerRequest(@NotBlank String questionId, @NotBlank String answer) {
}
