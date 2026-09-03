package com.fundit.auth.infrastructure.persistence.refreshtoken;

import com.fundit.auth.infrastructure.persistence.account.AccountJpaEntity;
import com.fundit.auth.infrastructure.persistence.account.AccountJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DELETE ... RETURNING이 실제로 account_id를 반환하는지 검증하는 통합 테스트.
 * RefreshTokenJpaRepository의 deleteAndReturnAccountId()는 @Modifying 없이 선언했는데
 * (Hibernate가 executeQuery()로 실행해 RETURNING 결과를 읽게 하기 위함), 이 트릭이
 * 실제 Postgres에서 정말 동작하는지는 실행해보기 전까진 확신할 수 없어 별도로 검증한다.
 *
 * jwt.secret/member-service.base-url을 {@code @TestPropertySource}로 고정하는 이유:
 * 공통 application.yml의 spring.profiles.active=local이 CI 체크아웃 트리에 없는
 * application-local.yml을 가리켜서, 이 값들을 채워줄 프로필 파일이 없으면 전체 컨텍스트
 * 로딩 자체가 PlaceholderResolutionException으로 실패한다(CI에서 실제로 재현됨).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long!!",
        "jwt.access-token-ttl=30m",
        "jwt.refresh-token-ttl=14d",
        "member-service.base-url=http://localhost:8082"
})
class RefreshTokenJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;
    @Autowired
    private AccountJpaRepository accountJpaRepository;

    private UUID createAccount() {
        AccountJpaEntity account = AccountJpaEntity.builder()
                .id(UUID.randomUUID())
                .email(UUID.randomUUID() + "@fundit.com")
                .passwordHash("hash")
                .role("member")
                .build();
        return accountJpaRepository.save(account).getId();
    }

    @Test
    void 만료전_토큰이면_RETURNING으로_account_id를_반환하고_행을_삭제한다() {
        // given
        UUID accountId = createAccount();
        UUID tokenId = UUID.randomUUID();
        refreshTokenJpaRepository.save(RefreshTokenJpaEntity.builder()
                .tokenId(tokenId)
                .accountId(accountId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build());

        // when
        Optional<UUID> result = refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId);

        // then
        assertThat(result).contains(accountId);
        assertThat(refreshTokenJpaRepository.findById(tokenId)).isEmpty();
    }

    @Test
    void 이미_삭제된_토큰이면_빈값을_반환한다() {
        // given
        UUID tokenId = UUID.randomUUID();

        // when
        Optional<UUID> result = refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 만료된_토큰이면_삭제되지_않고_빈값을_반환한다() {
        // given
        UUID accountId = createAccount();
        UUID tokenId = UUID.randomUUID();
        refreshTokenJpaRepository.save(RefreshTokenJpaEntity.builder()
                .tokenId(tokenId)
                .accountId(accountId)
                .expiresAt(Instant.now().minusSeconds(1))
                .createdAt(Instant.now())
                .build());

        // when
        Optional<UUID> result = refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId);

        // then
        assertThat(result).isEmpty();
        assertThat(refreshTokenJpaRepository.findById(tokenId)).isPresent();
    }

    @Test
    void deleteAllByAccountId는_해당_계정의_모든_refresh_token을_삭제한다() {
        // given
        UUID accountId = createAccount();
        refreshTokenJpaRepository.save(RefreshTokenJpaEntity.builder()
                .tokenId(UUID.randomUUID()).accountId(accountId)
                .expiresAt(Instant.now().plusSeconds(3600)).createdAt(Instant.now()).build());
        refreshTokenJpaRepository.save(RefreshTokenJpaEntity.builder()
                .tokenId(UUID.randomUUID()).accountId(accountId)
                .expiresAt(Instant.now().plusSeconds(3600)).createdAt(Instant.now()).build());

        // when
        refreshTokenJpaRepository.deleteAllByAccountId(accountId);

        // then (다른 테스트가 남겨둔, 무관한 계정의 토큰까지 비었을 필요는 없다 — 이 계정 것만 확인)
        assertThat(refreshTokenJpaRepository.findAll())
                .noneMatch(token -> token.getAccountId().equals(accountId));
    }
}
