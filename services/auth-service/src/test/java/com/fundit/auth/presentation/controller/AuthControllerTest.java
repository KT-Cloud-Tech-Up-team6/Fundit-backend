package com.fundit.auth.presentation.controller;

import com.fundit.auth.application.email.EmailAvailabilityService;
import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.application.password.PasswordChangeService;
import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.token.TokenRefreshService;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.JwtAuthenticationFilter;
import com.fundit.auth.infrastructure.security.JwtProperties;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.auth.infrastructure.security.SecurityConfig;
import com.fundit.auth.presentation.GlobalExceptionHandler;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        RefreshTokenCookieFactory.class, GlobalExceptionHandler.class, AuthControllerTest.TestJwtConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private EmailAvailabilityService emailAvailabilityService;
    @MockitoBean
    private SignupService signupService;
    @MockitoBean
    private LoginService loginService;
    @MockitoBean
    private TokenRefreshService tokenRefreshService;
    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret("test-only-secret-key-at-least-32-bytes-long!!");
            properties.setAccessTokenTtl(Duration.ofMinutes(30));
            properties.setRefreshTokenTtl(Duration.ofDays(14));
            return properties;
        }
    }

    @Test
    void 이메일_형식이_올바르면_사용가능여부를_반환한다() throws Exception {
        when(emailAvailabilityService.isAvailable("new@fundit.com")).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "new@fundit.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원가입에_성공하면_토큰과_쿠키를_응답한다() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(signupService.signup(any())).thenReturn(
                new SignupService.SignupResult(accountId, memberId, "access-token", "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Abcdefg1",
                                  "email": "new@fundit.com",
                                  "verificationToken": "ignored",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "agreedTerms": ["TOS"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void 계정이_잠겨있으면_423과_해제시각을_응답한다() throws Exception {
        Instant lockedUntil = Instant.now().plusSeconds(600);
        when(loginService.login(any())).thenThrow(new AccountLockedException(lockedUntil));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@fundit.com", "password": "wrong-pw"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail.lockedUntil").exists());
    }

    @Test
    void refreshToken_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_쿠키가_있으면_새_토큰을_발급한다() throws Exception {
        when(tokenRefreshService.refresh("old-refresh-token"))
                .thenReturn(new TokenIssuer.IssuedTokens("new-access", "new-refresh"));

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void 인증토큰_없이_비밀번호_변경을_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_access_token으로_비밀번호_변경에_성공한다() throws Exception {
        UUID accountId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.issueAccessToken(accountId, Role.MEMBER);

        mockMvc.perform(patch("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
