package com.fundit.auth.application.login;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LoginService loginService;

    private Account account(int failedLoginCount, Instant lockedUntil, boolean mustChangePassword) {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .failedLoginCount(failedLoginCount)
                .lockedUntil(lockedUntil)
                .mustChangePassword(mustChangePassword)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void 이메일과_비밀번호가_일치하면_계정을_반환하고_실패카운트를_초기화한다() {
        // given
        Account account = account(3, null, true);
        when(accountRepository.findByEmail("test@fundit.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-pw", "hash")).thenReturn(true);

        // when
        Account result = loginService.authenticate("test@fundit.com", "correct-pw");

        // then
        assertThat(result.getId()).isEqualTo(account.getId());
        assertThat(result.isMustChangePassword()).isTrue();
        assertThat(account.getFailedLoginCount()).isZero();
        verify(accountRepository).save(account);
    }

    @Test
    void 잠금이_풀린_계정이면_로그인이_정상_처리된다() {
        // given
        Account account = account(0, Instant.now().minusSeconds(60), false);
        when(accountRepository.findByEmail(any())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        // when
        Account result = loginService.authenticate("test@fundit.com", "correct-pw");

        // then
        assertThat(result.getId()).isEqualTo(account.getId());
    }
}
