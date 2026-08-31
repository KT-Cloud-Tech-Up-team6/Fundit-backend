package com.fundit.auth.application.password;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    @Test
    void 현재_비밀번호가_일치하면_새_비밀번호로_교체하고_강제변경_플래그를_해제한다() {
        // given
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId).email("test@fundit.com").passwordHash("old-hash")
                .role(Role.MEMBER).mustChangePassword(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-pw", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-pw")).thenReturn("new-hash");

        // when
        passwordChangeService.changePassword(accountId, "current-pw", "new-pw");

        // then
        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(account.isMustChangePassword()).isFalse();
        verify(accountRepository).save(account);
    }
}
