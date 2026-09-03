package com.fundit.auth.presentation;

import com.fundit.auth.infrastructure.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 로그인/회원가입/토큰재발급 3곳에서 공통으로 쓰는 Refresh Token 쿠키 조립. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private final JwtProperties jwtProperties;

    public ResponseCookie build(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/token/refresh")
                .maxAge(jwtProperties.getRefreshTokenTtl())
                .build();
    }
}
