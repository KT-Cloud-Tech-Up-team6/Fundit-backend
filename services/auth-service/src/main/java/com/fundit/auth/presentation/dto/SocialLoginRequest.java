package com.fundit.auth.presentation.dto;

import com.fundit.auth.domain.account.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
        @NotNull SocialProvider provider,
        @NotBlank String authorizationCode
) {
}
