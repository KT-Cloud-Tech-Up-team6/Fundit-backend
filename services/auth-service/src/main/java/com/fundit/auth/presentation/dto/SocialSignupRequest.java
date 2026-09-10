package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * {@code email}은 제공자가 이메일을 주지 않은 경우(카카오 이메일 미동의)에만 필요하다 —
 * 제공자가 준 값이 있으면 그쪽이 우선이고 이 필드는 무시된다.
 */
public record SocialSignupRequest(
        @NotBlank String signupToken,
        @NotBlank String verificationToken,
        @Email String email,
        @NotEmpty List<String> agreedTerms,
        Map<String, Object> address
) {
}
