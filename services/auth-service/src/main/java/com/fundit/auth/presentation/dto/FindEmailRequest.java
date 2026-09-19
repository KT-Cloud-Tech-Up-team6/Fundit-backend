package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** 이메일 찾기 1단계(AUTH-009) — 본인인증 전이라 사용자가 입력한 값이다. */
public record FindEmailRequest(@NotBlank String name, @NotBlank String phoneNumber) {
}
