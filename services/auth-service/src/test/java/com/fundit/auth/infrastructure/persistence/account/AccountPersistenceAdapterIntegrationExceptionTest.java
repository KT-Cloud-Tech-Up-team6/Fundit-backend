package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.JwtTestKeys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DB 제약조건 위반처럼 통합 계층에서만 재현되는 예외를 모은다(test-convention.md). */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "auth.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "member-service.base-url=http://localhost:8082",
        "internal-api.key=test-only-internal-api-key"
})
@Transactional
class AccountPersistenceAdapterIntegrationExceptionTest {

    @DynamicPropertySource
    static void jwtKey(DynamicPropertyRegistry registry) {
        JwtTestKeys.register(registry);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager entityManager;

    private Account newAccount(String email) {
        Instant now = Instant.now();
        return Account.builder()
                .id(UUID.randomUUID())
                .email(email)
                .verifiedName("김펀딧")
                .verifiedPhoneNumber("010-1234-5678")
                .passwordHash("bcrypt-hash")
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void 이메일_중복은_해시_UNIQUE가_막는다() {
        // given — 평문 UNIQUE(uq_accounts_email)를 지우고 해시로 옮겼다.
        // 암호문은 매번 달라 UNIQUE를 걸 수 없으므로 이 제약이 유일한 방어선이다
        accountRepository.save(newAccount("dup@fundit.com"));

        // when & then
        assertThatThrownBy(() -> {
            accountRepository.save(newAccount("dup@fundit.com"));
            entityManager.flush();
        }).hasMessageContaining("uq_accounts_email_hash");
    }
}
