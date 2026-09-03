package com.fundit.auth.presentation.controller;

import com.fundit.auth.application.email.EmailAvailabilityService;
import com.fundit.auth.application.identity.IdentityVerificationService;
import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.application.password.PasswordChangeService;
import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.token.TokenRefreshService;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.infrastructure.security.AccountAuthenticationProvider;
import com.fundit.auth.infrastructure.security.JwtAuthenticationFilter;
import com.fundit.auth.infrastructure.security.JwtProperties;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.auth.infrastructure.security.LoginFailureHandler;
import com.fundit.auth.infrastructure.security.LoginSuccessHandler;
import com.fundit.auth.infrastructure.security.SecurityConfig;
import com.fundit.auth.presentation.GlobalExceptionHandler;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import com.fundit.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예외/실패 흐름만 검증한다. 정상 흐름은 {@link AuthControllerTest} 참고.
 * jwt.* 를 {@code @TestPropertySource}로 고정하는 이유는 {@link AuthControllerTest} 클래스 주석 참고.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, JwtProperties.class,
        AccountAuthenticationProvider.class, LoginSuccessHandler.class, LoginFailureHandler.class,
        RefreshTokenCookieFactory.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long!!",
        "jwt.access-token-ttl=30m",
        "jwt.refresh-token-ttl=14d"
})
class AuthControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailAvailabilityService emailAvailabilityService;
    @MockitoBean
    private IdentityVerificationService identityVerificationService;
    @MockitoBean
    private SignupService signupService;
    @MockitoBean
    private LoginService loginService;
    @MockitoBean
    private TokenIssuer tokenIssuer;
    @MockitoBean
    private TokenRefreshService tokenRefreshService;
    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_비밀번호가_틀리면_401_INVALID_CREDENTIALS를_반환한다() throws Exception {
        // given
        when(loginService.authenticate("test@fundit.com", "wrong-pw"))
                .thenThrow(new BusinessException(com.fundit.auth.domain.AuthErrorCode.INVALID_CREDENTIALS));

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@fundit.com", "password": "wrong-pw"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 계정이_잠겨있으면_423과_해제시각을_응답한다() throws Exception {
        // given
        Instant lockedUntil = Instant.now().plusSeconds(600);
        when(loginService.authenticate("test@fundit.com", "wrong-pw")).thenThrow(new AccountLockedException(lockedUntil));

        // when & then
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
        // when & then
        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증토큰_없이_비밀번호_변경을_요청하면_401_UNAUTHORIZED를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 만료된_access_token이면_401_TOKEN_EXPIRED를_반환한다() throws Exception {
        // given
        JwtProperties expiredTokenProperties = new JwtProperties();
        expiredTokenProperties.setSecret("test-only-secret-key-at-least-32-bytes-long!!");
        expiredTokenProperties.setAccessTokenTtl(Duration.ofSeconds(-1));
        expiredTokenProperties.setRefreshTokenTtl(Duration.ofDays(14));
        String expiredToken = new JwtTokenProvider(expiredTokenProperties)
                .issueAccessToken(UUID.randomUUID(), com.fundit.auth.domain.account.Role.MEMBER);

        // when & then
        mockMvc.perform(patch("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void 서명이_잘못된_access_token이면_401_TOKEN_INVALID를_반환한다() throws Exception {
        // given
        JwtProperties otherSecretProperties = new JwtProperties();
        otherSecretProperties.setSecret("a-completely-different-signing-secret-32bytes!!");
        otherSecretProperties.setAccessTokenTtl(Duration.ofMinutes(30));
        otherSecretProperties.setRefreshTokenTtl(Duration.ofDays(14));
        String forgedToken = new JwtTokenProvider(otherSecretProperties)
                .issueAccessToken(UUID.randomUUID(), com.fundit.auth.domain.account.Role.MEMBER);

        // when & then
        mockMvc.perform(patch("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + forgedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
