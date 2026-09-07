package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record LiveVerificationCreateRequest(@NotBlank String questionSummaryId, @NotBlank String answer) {
}
