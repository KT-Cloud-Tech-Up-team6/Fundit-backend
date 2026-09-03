package com.fundit.auth.infrastructure.security;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 인증 성공 시 Access/Refresh Token을 발급하고 응답(바디+쿠키)을 조립한다. */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException {
        Account account = (Account) authResult.getPrincipal();
        var tokens = tokenIssuer.issue(account.getId(), account.getRole());

        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.build(tokens.refreshToken()).toString());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new LoginResponseBody(tokens.accessToken(), account.isMustChangePassword()));
    }

    private record LoginResponseBody(String accessToken, boolean mustChangePassword) {
    }
}
