package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * {@code email}/{@code nickname}은 제공자가 주지 않은 경우에만 필요하다 —
 * 제공자가 준 값이 있으면 그쪽이 우선이고 이 필드는 무시된다.
 * (카카오는 이메일이 비즈니스 앱 전환 없이는 불가하고, 닉네임은 동의항목 설정에 달려 있다.)
 */
public record SocialSignupRequest(
        @NotBlank String signupToken,
        @NotBlank String verificationToken,
        @Email String email,
        @Size(max = 50) String nickname,
        @NotEmpty List<String> agreedTerms,
        Map<String, Object> address
) {
}
