package com.fundit.auth.presentation.dto;

/**
 * 가입된 계정이 없으면 {@code maskedEmail}이 null이다 — 계정 유무와 무관하게
 * 응답 <b>형태</b>는 같게 유지한다(AUTH-009).
 */
public record FindEmailResponse(String maskedEmail) {
}
