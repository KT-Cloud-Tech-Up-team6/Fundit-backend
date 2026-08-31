package com.fundit.auth.infrastructure.security;

import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationProviderExceptionTest {

    @Mock
    private LoginService loginService;

    @InjectMocks
    private AccountAuthenticationProvider provider;

    @Test
    void 계정이_잠겨있으면_AccountLockedAuthenticationException으로_변환한다() {
        // given
        Instant lockedUntil = Instant.now().plusSeconds(600);
        when(loginService.authenticate("test@fundit.com", "pw")).thenThrow(new AccountLockedException(lockedUntil));

        // when & then
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("test@fundit.com", "pw")))
                .isInstanceOf(AccountLockedAuthenticationException.class)
                .extracting(e -> ((AccountLockedAuthenticationException) e).getLockedUntil())
                .isEqualTo(lockedUntil);
    }

    @Test
    void 자격증명이_틀리면_BadCredentialsException으로_변환한다() {
        // given
        when(loginService.authenticate("test@fundit.com", "wrong-pw"))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        // when & then
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("test@fundit.com", "wrong-pw")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void supports는_다른_인증토큰타입을_지원하지_않는다() {
        assertThat(provider.supports(org.springframework.security.authentication.TestingAuthenticationToken.class))
                .isFalse();
    }
}
