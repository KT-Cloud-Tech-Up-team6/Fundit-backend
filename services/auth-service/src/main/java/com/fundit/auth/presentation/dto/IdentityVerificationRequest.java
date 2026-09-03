package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record IdentityVerificationRequest(@NotBlank String identityVerificationId) {
}
