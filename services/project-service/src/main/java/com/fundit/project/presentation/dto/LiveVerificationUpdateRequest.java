package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record LiveVerificationUpdateRequest(@NotBlank String answer) {
}
