package com.fundit.auth.presentation.controller;

import com.fundit.auth.application.email.EmailAvailabilityService;
import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.application.password.PasswordChangeService;
import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.token.TokenRefreshService;
import com.fundit.auth.presentation.RefreshTokenCookieFactory;
import com.fundit.auth.presentation.dto.CheckEmailResponse;
import com.fundit.auth.presentation.dto.LoginRequest;
import com.fundit.auth.presentation.dto.LoginResponse;
import com.fundit.auth.presentation.dto.MessageResponse;
import com.fundit.auth.presentation.dto.PasswordChangeRequest;
import com.fundit.auth.presentation.dto.SignupRequest;
import com.fundit.auth.presentation.dto.SignupResponse;
import com.fundit.auth.presentation.dto.TokenRefreshResponse;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailAvailabilityService emailAvailabilityService;
    private final SignupService signupService;
    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @GetMapping("/check-email")
    public CheckEmailResponse checkEmail(@RequestParam @NotBlank @Email String email) {
        return new CheckEmailResponse(emailAvailabilityService.isAvailable(email));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        var result = signupService.signup(new SignupService.SignupCommand(
                request.email(), request.password(), request.name(), request.phoneNumber(),
                request.agreedTerms(), request.address()));

        return withRefreshTokenCookie(result.refreshToken())
                .body(new SignupResponse(result.accountId(), result.memberId(), result.accessToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = loginService.login(new LoginService.LoginCommand(request.email(), request.password()));

        return withRefreshTokenCookie(result.refreshToken())
                .body(new LoginResponse(result.accessToken(), result.mustChangePassword()));
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
