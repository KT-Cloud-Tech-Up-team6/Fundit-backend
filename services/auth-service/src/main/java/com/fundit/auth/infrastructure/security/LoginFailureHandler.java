package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** 로그인 실패를 SecurityConfig.onAuthenticationFailure(보호 엔드포인트 401)와 동일한 응답 규격으로 변환한다. */
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        if (exception instanceof AccountLockedAuthenticationException locked) {
            response.setStatus(AuthErrorCode.ACCOUNT_LOCKED.getHttpStatus());
            objectMapper.writeValue(response.getWriter(),
                    ErrorResponse.of(AuthErrorCode.ACCOUNT_LOCKED, Map.of("lockedUntil", locked.getLockedUntil())));
            return;
        }

        response.setStatus(AuthErrorCode.INVALID_CREDENTIALS.getHttpStatus());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(AuthErrorCode.INVALID_CREDENTIALS));
    }
}
