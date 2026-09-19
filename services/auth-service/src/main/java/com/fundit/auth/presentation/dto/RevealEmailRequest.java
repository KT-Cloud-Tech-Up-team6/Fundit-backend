package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 찾기 2단계(AUTH-009) — 본인인증 토큰만 받는다.
 *
 * <p>이름·전화번호를 다시 받지 않는 이유: 본문으로 받으면 1단계에서 알아낸 남의 번호에
 * 자기 인증 토큰을 붙여 전문을 꺼낼 수 있다. 조회에 쓰는 값은 토큰 안에 든 본인인증 결과다.
 */
public record RevealEmailRequest(@NotBlank String verificationToken) {
}
