package com.fundit.auth.infrastructure.security;

import lombok.Getter;
import org.springframework.security.authentication.LockedException;

import java.time.Instant;

/**
 * 로그인 필터 체계에서 계정 잠금을 표현하는 Spring Security 예외.
 * 도메인 예외 {@link com.fundit.auth.domain.account.AccountLockedException}과 별개로 두는 이유는
 * AuthenticationProvider가 던지는 예외는 AuthenticationException 계열이어야
 * ExceptionTranslationFilter/AuthenticationFailureHandler가 처리하기 때문이다.
 */
@Getter
public class AccountLockedAuthenticationException extends LockedException {

    private final Instant lockedUntil;

    public AccountLockedAuthenticationException(Instant lockedUntil) {
        super("계정이 잠겨 있습니다.");
        this.lockedUntil = lockedUntil;
    }
}
