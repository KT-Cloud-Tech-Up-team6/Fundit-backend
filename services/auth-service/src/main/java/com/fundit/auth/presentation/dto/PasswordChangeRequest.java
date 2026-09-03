package com.fundit.auth.presentation.dto;

import com.fundit.auth.presentation.dto.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank @ValidPassword String newPassword
) {
}
