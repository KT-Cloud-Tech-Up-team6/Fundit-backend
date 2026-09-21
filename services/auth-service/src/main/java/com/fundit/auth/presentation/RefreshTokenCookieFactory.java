package com.fundit.auth.presentation;

import com.fundit.auth.infrastructure.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인/회원가입/토큰재발급/로그아웃에서 공통으로 쓰는 Refresh Token 쿠키 조립.
 *
 * <p>path가 {@code /api/v1/auth}인 이유: 재발급({@code /token/refresh})과 로그아웃({@code /logout})
 * 두 곳이 모두 이 쿠키를 받아야 한다. 재발급 경로로만 좁히면 브라우저가 로그아웃 요청에 쿠키를
 * 싣지 않아 서버가 폐기할 토큰을 알 수 없다. auth-service 안으로만 한정되고 HttpOnly·Strict는 유지한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie build(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(jwtProperties.getRefreshTokenTtl())
                .build();
    }

    /** 로그아웃 응답용 — 같은 이름·path로 즉시 만료시켜 브라우저에서 지운다. */
    public ResponseCookie expire() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build();
    }
}
