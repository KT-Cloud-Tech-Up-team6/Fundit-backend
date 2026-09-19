package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 링크 발송(AUTH-010). 세 값이 모두 맞아야 발송된다. */
public record PasswordResetRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber,
        @NotBlank @Email String email
) {
}
