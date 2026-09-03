package com.fundit.auth.application.login;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 자격증명 검증만 담당한다. 토큰 발급은 Spring Security 인증 필터 체계의
 * 성공 핸들러(LoginSuccessHandler)로 옮겨졌다 — AuthenticationProvider는
 * "인증됐다/안됐다"만 판단해야 하므로, 부수효과(토큰 발급)를 이 안에 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public Account authenticate(String email, String password) {
        // anti-enumeration(security.md S10): 이메일 없음/비밀번호 불일치를 구분하지 않고 동일한 예외로 응답
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        Instant now = Instant.now();
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            account.recordFailedLogin(now);
            accountRepository.save(account);
            if (account.isLocked(now)) {
                throw new AccountLockedException(account.getLockedUntil());
            }
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        account.recordSuccessfulLogin();
        accountRepository.save(account);
        return account;
    }
}
