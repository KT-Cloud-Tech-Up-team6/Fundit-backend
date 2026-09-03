package com.fundit.auth.application.email;

import com.fundit.auth.domain.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailAvailabilityService {

    private final AccountRepository accountRepository;

    public boolean isAvailable(String email) {
        return !accountRepository.existsByEmail(email);
    }
}
