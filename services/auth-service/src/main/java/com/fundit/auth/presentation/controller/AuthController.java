package com.fundit.auth.presentation.controller;

import com.fundit.auth.application.email.EmailAvailabilityService;
import com.fundit.auth.application.email.EmailFindService;
import com.fundit.auth.application.identity.IdentityVerificationService;
import com.fundit.auth.application.password.PasswordChangeService;
import com.fundit.auth.application.password.PasswordResetService;
import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.application.social.SocialLinkService;
import com.fundit.auth.application.social.SocialLoginService;
import com.fundit.auth.application.social.SocialSignupService;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.token.TokenLogoutService;
import com.fundit.auth.application.token.TokenRefreshService;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import com.fundit.auth.presentation.dto.CheckEmailResponse;
import com.fundit.auth.presentation.dto.FindEmailRequest;
import com.fundit.auth.presentation.dto.FindEmailResponse;
import com.fundit.auth.presentation.dto.IdentityVerificationRequest;
import com.fundit.auth.presentation.dto.IdentityVerificationResponse;
import com.fundit.auth.presentation.dto.MessageResponse;
import com.fundit.auth.presentation.dto.PasswordResetConfirmRequest;
import com.fundit.auth.presentation.dto.PasswordResetRequest;
import com.fundit.auth.presentation.dto.RevealEmailRequest;
import com.fundit.auth.presentation.dto.RevealEmailResponse;
import com.fundit.auth.presentation.dto.PasswordChangeRequest;
import com.fundit.auth.presentation.dto.SignupRequest;
import com.fundit.auth.presentation.dto.SignupResponse;
import com.fundit.auth.presentation.dto.SocialLinkRequest;
import com.fundit.auth.presentation.dto.SocialLinkResponse;
import com.fundit.auth.presentation.dto.SocialLoginRequest;
import com.fundit.auth.presentation.dto.SocialLoginResponse;
import com.fundit.auth.presentation.dto.SocialSignupRequest;
import com.fundit.auth.presentation.dto.SocialSignupResponse;
import com.fundit.auth.presentation.dto.TokenRefreshResponse;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 태그를 명시하는 이유: 기본값은 클래스명에서 파생된 "auth-controller"인데,
// 필터가 처리하는 로그인(AuthOpenApiCustomizer)도 같은 그룹에 넣어야 프론트 클라이언트가 쪼개지지 않는다.
@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailAvailabilityService emailAvailabilityService;
    private final EmailFindService emailFindService;
    private final IdentityVerificationService identityVerificationService;
    private final SignupService signupService;
    private final TokenRefreshService tokenRefreshService;
    private final PasswordChangeService passwordChangeService;
    private final PasswordResetService passwordResetService;
    private final SocialLoginService socialLoginService;
    private final SocialSignupService socialSignupService;
    private final SocialLinkService socialLinkService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final TokenLogoutService tokenLogoutService;

    @GetMapping("/check-email")
    public CheckEmailResponse checkEmail(@RequestParam @NotBlank @Email String email) {
        return new CheckEmailResponse(emailAvailabilityService.isAvailable(email));
    }

    /**
     * 이메일 찾기 1단계(AUTH-009). 본인인증 <b>전</b>이라 마스킹된 값만 돌려준다.
     * 가입 계정이 없어도 200이고 {@code maskedEmail}만 null이다.
     */
    @PostMapping("/find-email")
    public FindEmailResponse findEmail(@Valid @RequestBody FindEmailRequest request) {
        return new FindEmailResponse(emailFindService.findMasked(request.name(), request.phoneNumber()));
    }

    /** 이메일 찾기 2단계(AUTH-009). 본인인증 토큰을 소비하고 전문을 돌려준다. */
    @PostMapping("/find-email/reveal")
    public RevealEmailResponse revealEmail(@Valid @RequestBody RevealEmailRequest request) {
        return new RevealEmailResponse(emailFindService.reveal(request.verificationToken()));
    }

    /**
     * 비밀번호 재설정 링크 발송(AUTH-010). 계정이 없거나 값이 안 맞아도 <b>같은 응답</b>이다 —
     * 여기서 404를 주면 이메일을 넣어보며 가입 여부를 캐낼 수 있다.
     */
    @PostMapping("/reset-password")
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.name(), request.phoneNumber(), request.email());
        return new MessageResponse("입력하신 정보와 일치하는 계정이 있다면 재설정 링크를 보내드립니다.");
    }

    /** 메일 링크로 새 비밀번호를 설정한다(AUTH-010). 토큰은 1회용이다. */
    @PostMapping("/reset-password/confirm")
    public MessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return new MessageResponse("비밀번호가 변경되었습니다.");
    }

    @PostMapping("/identity-verifications")
    public IdentityVerificationResponse verifyIdentity(@Valid @RequestBody IdentityVerificationRequest request) {
        var result = identityVerificationService.verify(request.identityVerificationId());
        return new IdentityVerificationResponse(result.verificationToken(), result.expiresAt());
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        var result = signupService.signup(new SignupService.SignupCommand(
                request.email(), request.password(), request.verificationToken(), request.name(),
                request.nickname(), request.phoneNumber(), request.agreedTerms(), request.address()));

        return withRefreshTokenCookie(result.refreshToken())
                .body(new SignupResponse(result.accountId(), result.memberId(), result.accessToken()));
    }

    /**
     * 미가입도 200으로 응답한다(명세 AUTH-002) — 404로 주면 프론트가 에러 경로로 빠져
     * 가입 화면 전환이 어색해진다. 인가 코드는 1회용이라 재시도도 불가능하다.
     */
    @PostMapping("/login/social")
    public ResponseEntity<SocialLoginResponse> loginSocial(@Valid @RequestBody SocialLoginRequest request) {
        var result = socialLoginService.login(request.provider(), request.authorizationCode());
        // 로그인이 끝난 경우에만 쿠키를 내린다. 가입·연동이 필요한 응답은 아직 인증이 아니라
        // refreshToken이 null이고, 그대로 쿠키를 만들면 빈 값이 기존 refreshToken을 덮어쓴다.
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(SocialLoginResponse.from(result));
        }
        return withRefreshTokenCookie(result.refreshToken()).body(SocialLoginResponse.from(result));
    }

    @PostMapping("/signup/social")
    public ResponseEntity<SocialSignupResponse> signupSocial(@Valid @RequestBody SocialSignupRequest request) {
        var result = socialSignupService.signup(new SocialSignupService.SocialSignupCommand(
                request.signupToken(), request.email(), request.name(),
                request.nickname(), request.phoneNumber(), request.agreedTerms(), request.address()));

        return withRefreshTokenCookie(result.refreshToken())
                .body(new SocialSignupResponse(result.accountId(), result.memberId(), result.accessToken()));
    }

    /**
     * 정책 A — 기존 자체가입 계정에 소셜 로그인을 붙인다.
     * 본인인증이 그 계정 주인의 것인지 확인되어야만 성공한다.
     */
    @PostMapping("/social/link")
    public ResponseEntity<SocialLinkResponse> linkSocial(@Valid @RequestBody SocialLinkRequest request) {
        var tokens = socialLinkService.link(request.linkToken(), request.verificationToken());
        return withRefreshTokenCookie(tokens.refreshToken()).body(new SocialLinkResponse(tokens.accessToken()));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }

        TokenIssuer.IssuedTokens tokens = tokenRefreshService.refresh(refreshToken);

        return withRefreshTokenCookie(tokens.refreshToken())
                .body(new TokenRefreshResponse(tokens.accessToken()));
    }

    /**
     * 로그아웃 — 이 기기의 refresh 토큰을 폐기하고 쿠키를 지운다. access 토큰이 이미 만료된 상태에서도
     * 호출할 수 있어야 해서 인증을 요구하지 않는다(SecurityConfig). 쿠키가 없어도 200이다(멱등).
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        tokenLogoutService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
                .body(new MessageResponse("로그아웃되었습니다."));
    }

    @PatchMapping("/password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal UUID accountId,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.changePassword(accountId, request.currentPassword(), request.newPassword());
        return new MessageResponse("비밀번호가 변경되었습니다.");
    }

    private ResponseEntity.BodyBuilder withRefreshTokenCookie(String refreshToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.build(refreshToken).toString());
    }
}
