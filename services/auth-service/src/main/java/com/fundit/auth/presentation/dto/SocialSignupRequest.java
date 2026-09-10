package com.fundit.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 두 선택 필드의 우선순위가 <b>반대</b>다.
 *
 * <ul>
 *   <li>{@code email} — <b>제공자 값이 우선.</b> 로그인 식별자라 사용자가 다른 값을 넣으면
 *       소셜 계정과 로그인 이메일이 갈라진다. 제공자가 안 준 경우(카카오는 비즈니스 앱
 *       전환 없이는 불가)에만 이 값을 쓴다 — 프론트는 제공자 이메일이 있으면 읽기 전용으로 둘 것.</li>
 *   <li>{@code nickname} — <b>입력값이 우선.</b> 사용자가 정하는 표시명이고, 프론트는 제공자
 *       닉네임으로 폼을 미리 채운다. 사용자가 고쳐 보냈으면 그게 선택이다. 비어 있으면
 *       제공자 값으로 채운다.</li>
 * </ul>
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
