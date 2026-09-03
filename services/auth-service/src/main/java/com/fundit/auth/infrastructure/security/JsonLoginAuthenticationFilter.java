package com.fundit.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * POST /api/v1/auth/login 전용 — JSON 바디({"email","password"})를 읽어 AuthenticationManager로
 * 위임한다. 입력 검증은 null/blank 체크만 한다(사용자 확정) — 이메일 형식 오류 등은
 * 계정 조회 실패로 자연스럽게 401 INVALID_CREDENTIALS로 이어진다.
 */
public class JsonLoginAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper;

    public JsonLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler) {
        super(authenticationManager);
        this.objectMapper = objectMapper;
        setFilterProcessesUrl("/api/v1/auth/login");
        setAuthenticationSuccessHandler(successHandler);
        setAuthenticationFailureHandler(failureHandler);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        LoginRequestBody body;
        try {
            body = objectMapper.readValue(request.getInputStream(), LoginRequestBody.class);
        } catch (IOException e) {
            throw new AuthenticationServiceException("로그인 요청 본문을 읽을 수 없습니다.", e);
        }

        if (body == null || !StringUtils.hasText(body.email()) || !StringUtils.hasText(body.password())) {
            throw new AuthenticationServiceException("이메일과 비밀번호는 필수입니다.");
        }

        var authRequest = new UsernamePasswordAuthenticationToken(body.email(), body.password());
        return getAuthenticationManager().authenticate(authRequest);
    }

    private record LoginRequestBody(String email, String password) {
    }
}
