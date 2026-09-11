package com.fundit.member.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 휴대폰번호를 URL이 아니라 본문으로 받는다 — 쿼리스트링은 액세스 로그에 그대로 남는다(security.md S10).
 */
public record PhoneVerificationRequest(@NotBlank String phoneNumber) {
}
