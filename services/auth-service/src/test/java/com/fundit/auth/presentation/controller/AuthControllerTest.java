package com.fundit.auth.presentation.controller;

import com.fundit.auth.application.email.EmailAvailabilityService;
import com.fundit.auth.application.identity.IdentityVerificationService;
import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.application.password.PasswordChangeService;
import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.token.TokenRefreshService;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.AccountAuthenticationProvider;
import com.fundit.auth.infrastructure.security.JwtAuthenticationFilter;
import com.fundit.auth.infrastructure.security.JwtProperties;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.auth.infrastructure.security.LoginFailureHandler;
import com.fundit.auth.infrastructure.security.LoginSuccessHandler;
import com.fundit.auth.infrastructure.security.SecurityConfig;
import com.fundit.auth.presentation.GlobalExceptionHandler;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정상 흐름만 검증한다. 예외/실패 흐름은 {@link AuthControllerExceptionTest} 참고.
 *
 * jwt.* 는 {@code @ConfigurationProperties}라서 {@code @TestConfiguration}으로 만든 빈에
 * 값을 수동 세팅해도, 실제 프로필 파일(application-local.yml 등)에 같은 키가 있으면
 * ConfigurationPropertiesBindingPostProcessor가 그 값으로 다시 덮어써버린다(로컬에만
 * application-local.yml이 있는 개발자 환경에서 특히 조용히 재현됨). 그래서 빈 오버라이드
 * 대신 {@code @TestPropertySource}로 최우선순위 프로퍼티를 직접 주입한다.
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    void 이메일_형식이_올바르면_사용가능여부를_반환한다() throws Exception {
        // given
        when(emailAvailabilityService.isAvailable("new@fundit.com")).thenReturn(true);

        // when & then
        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "new@fundit.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 본인인증_결과조회에_성공하면_인증토큰을_반환한다() throws Exception {
        // given
        Instant expiresAt = Instant.parse("2026-08-26T10:15:00Z");
        when(identityVerificationService.verify("identity-verification-1")).thenReturn(
                new IdentityVerificationService.IdentityVerificationResult("verify-token", expiresAt));

        // when & then
        mockMvc.perform(post("/api/v1/auth/identity-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identityVerificationId": "identity-verification-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").value("verify-token"));
    }

    @Test
    void 회원가입에_성공하면_토큰과_쿠키를_응답한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(signupService.signup(org.mockito.ArgumentMatchers.any())).thenReturn(
                new SignupService.SignupResult(accountId, memberId, "access-token", "refresh-token"));

        // when & then
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
    void 로그인에_성공하면_토큰과_쿠키를_응답한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .mustChangePassword(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(loginService.authenticate("test@fundit.com", "correct-pw")).thenReturn(account);
        when(tokenIssuer.issue(accountId, Role.MEMBER))
                .thenReturn(new TokenIssuer.IssuedTokens("access-token", "refresh-token"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@fundit.com", "password": "correct-pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void refreshToken_쿠키가_있으면_새_토큰을_발급한다() throws Exception {
        // given
        when(tokenRefreshService.refresh("old-refresh-token"))
                .thenReturn(new TokenIssuer.IssuedTokens("new-access", "new-refresh"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void 유효한_access_token으로_비밀번호_변경에_성공한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.issueAccessToken(accountId, Role.MEMBER);

        // when & then
        mockMvc.perform(patch("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Abcdefg1", "newPassword": "Newpass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
