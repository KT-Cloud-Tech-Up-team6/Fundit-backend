package com.fundit.auth.application.password;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        account.changePassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }
}
