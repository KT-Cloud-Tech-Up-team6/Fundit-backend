package com.fundit.auth.application.password;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceExceptionTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    @Test
    void 계정이_존재하지_않으면_예외가_발생한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordChangeService.changePassword(accountId, "current-pw", "new-pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 현재_비밀번호가_틀리면_예외가_발생하고_저장하지_않는다() {
        // given
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId).email("test@fundit.com").passwordHash("old-hash")
                .role(Role.MEMBER).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-pw", "old-hash")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> passwordChangeService.changePassword(accountId, "wrong-pw", "new-pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(accountRepository, never()).save(any());
    }
}
