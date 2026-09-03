package com.fundit.auth.application.login;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceUnitExceptionTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LoginService loginService;

    private Account account(int failedLoginCount, Instant lockedUntil) {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .failedLoginCount(failedLoginCount)
                .lockedUntil(lockedUntil)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void 존재하지_않는_이메일이면_INVALID_CREDENTIALS_예외가_발생한다() {
        // given
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginService.authenticate("none@fundit.com", "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 잠긴_계정이면_비밀번호_확인_없이_잠금예외가_발생한다() {
        // given
        Account account = account(0, Instant.now().plusSeconds(600));
        when(accountRepository.findByEmail(any())).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> loginService.authenticate("test@fundit.com", "pw"))
                .isInstanceOf(AccountLockedException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void 비밀번호가_틀리고_5회_미만이면_실패카운트만_증가하고_INVALID_CREDENTIALS_예외가_발생한다() {
        // given
        Account account = account(2, null);
        when(accountRepository.findByEmail(any())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> loginService.authenticate("test@fundit.com", "wrong-pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        assertThat(account.getFailedLoginCount()).isEqualTo(3);
        assertThat(account.isLocked(Instant.now())).isFalse();
    }

    @Test
    void 비밀번호가_틀리고_5회째면_계정잠금_예외가_발생한다() {
        // given
        Account account = account(4, null);
        when(accountRepository.findByEmail(any())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> loginService.authenticate("test@fundit.com", "wrong-pw"))
                .isInstanceOf(AccountLockedException.class);
        assertThat(account.isLocked(Instant.now())).isTrue();
    }
}
