package com.fundit.auth.application.login;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public LoginResult login(LoginCommand command) {
        // anti-enumeration(security.md S10): 이메일 없음/비밀번호 불일치를 구분하지 않고 동일한 예외로 응답
        Account account = accountRepository.findByEmail(command.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        Instant now = Instant.now();
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }

        if (!passwordEncoder.matches(command.password(), account.getPasswordHash())) {
            account.recordFailedLogin(now);
            accountRepository.save(account);
            if (account.isLocked(now)) {
                throw new AccountLockedException(account.getLockedUntil());
            }
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        account.recordSuccessfulLogin();
        accountRepository.save(account);

        var tokens = tokenIssuer.issue(account.getId(), account.getRole());
        return new LoginResult(tokens.accessToken(), tokens.refreshToken(), account.isMustChangePassword());
    }

    public record LoginCommand(String email, String password) {
    }

    public record LoginResult(String accessToken, String refreshToken, boolean mustChangePassword) {
    }
}
