package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPersistenceAdapterUnitTest {

    @Mock
    private AccountJpaRepository jpaRepository;

    private final AccountMapper mapper = new AccountMapper();

    private AccountPersistenceAdapter adapter() {
        return new AccountPersistenceAdapter(jpaRepository, mapper);
    }

    private Account domainAccount() {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void save는_엔티티로_변환해_저장하고_다시_도메인으로_변환해_반환한다() {
        // given
        Account account = domainAccount();
        AccountJpaEntity entity = mapper.toEntity(account);
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(entity);

        // when
        Account saved = adapter().save(account);

        // then
        assertThat(saved.getId()).isEqualTo(account.getId());
    }

    @Test
    void findById는_존재하면_도메인으로_변환해_반환한다() {
        // given
        Account account = domainAccount();
        when(jpaRepository.findById(account.getId())).thenReturn(Optional.of(mapper.toEntity(account)));

        // when
        Optional<Account> found = adapter().findById(account.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(account.getId());
    }

    @Test
    void findByEmail은_존재하지_않으면_빈값을_반환한다() {
        // given
        when(jpaRepository.findByEmail("none@fundit.com")).thenReturn(Optional.empty());

        // when
        Optional<Account> found = adapter().findByEmail("none@fundit.com");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail과_deleteById는_리포지토리로_위임한다() {
        // given
        UUID id = UUID.randomUUID();
        when(jpaRepository.existsByEmail("test@fundit.com")).thenReturn(true);

        // when & then
        assertThat(adapter().existsByEmail("test@fundit.com")).isTrue();
        adapter().deleteById(id);
        org.mockito.Mockito.verify(jpaRepository).deleteById(id);
    }
}
