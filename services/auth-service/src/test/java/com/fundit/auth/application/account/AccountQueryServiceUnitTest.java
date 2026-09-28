package com.fundit.auth.application.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountQueryService accountQueryService;

    @Test
    void 본인_계정의_이메일을_마스킹_없이_돌려준다() {
        // given
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId).email("test@fundit.com").passwordHash("hash")
                .role(Role.MEMBER).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // when
        String email = accountQueryService.getEmail(accountId);

        // then
        assertThat(email).isEqualTo("test@fundit.com");
    }
}
