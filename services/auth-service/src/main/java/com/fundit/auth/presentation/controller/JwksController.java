package com.fundit.auth.presentation.controller;

import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 토큰 서명을 검증할 공개키를 JWKS 형식으로 노출한다.
 *
 * <p>공개키만 담기므로 인증 없이 열어둔다(`SecurityConfig`에서 permitAll). 경로가
 * 표준 `/oauth2/jwks`나 `/.well-known/jwks.json`이 아닌 이유는 `api-convention.md`가
 * `/api/v1/` 프리픽스를 고정으로 두고 있어서다 — 이 JWKS는 외부 표준 클라이언트가 아니라
 * 우리 게이트웨이만 읽고, 그쪽에 URI를 설정으로 직접 주므로 표준 경로일 이점이 없다.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/api/v1/auth/jwks")
    public Map<String, Object> jwks() {
        return jwtTokenProvider.publicJwkSet();
    }
}
