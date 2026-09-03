package com.fundit.auth.infrastructure.security;

import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LoginService(도메인 예외 기반)와 Spring Security의 AuthenticationManager/Filter 체계를
 * 잇는 어댑터. 도메인 예외를 Spring Security 예외로 변환하는 역할만 한다.
 */
@Component
@RequiredArgsConstructor
public class AccountAuthenticationProvider implements AuthenticationProvider {

    private final LoginService loginService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());

        Account account;
        try {
            account = loginService.authenticate(email, password);
        } catch (AccountLockedException e) {
            throw new AccountLockedAuthenticationException(e.getLockedUntil());
        } catch (BusinessException e) {
            throw new BadCredentialsException(e.getMessage());
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
        return new UsernamePasswordAuthenticationToken(account, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
