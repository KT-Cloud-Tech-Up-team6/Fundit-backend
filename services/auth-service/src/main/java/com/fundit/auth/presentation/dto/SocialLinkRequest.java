package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 연동 대상 계정을 클라이언트가 지목하지 않는다 — accountId는 서버가 발급한 {@code linkToken}에만 들어 있다.
 */
public record SocialLinkRequest(
        @NotBlank String linkToken,
        @NotBlank String verificationToken
) {
}
