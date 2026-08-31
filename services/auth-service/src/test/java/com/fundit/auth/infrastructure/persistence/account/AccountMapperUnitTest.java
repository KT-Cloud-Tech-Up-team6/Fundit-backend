package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMapperUnitTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void 도메인을_엔티티로_변환하면_role이_소문자로_저장된다() {
        // given
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .failedLoginCount(1)
                .mustChangePassword(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // when
        AccountJpaEntity entity = mapper.toEntity(account);

        // then
        assertThat(entity.getRole()).isEqualTo("admin");
        assertThat(entity.getId()).isEqualTo(account.getId());
        assertThat(entity.getEmail()).isEqualTo(account.getEmail());
    }

    @Test
    void 엔티티를_도메인으로_변환하면_role이_대문자_enum으로_복원된다() {
        // given
        AccountJpaEntity entity = AccountJpaEntity.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role("member")
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // when
        Account account = mapper.toDomain(entity);

        // then
        assertThat(account.getRole()).isEqualTo(Role.MEMBER);
        assertThat(account.getId()).isEqualTo(entity.getId());
    }
}
