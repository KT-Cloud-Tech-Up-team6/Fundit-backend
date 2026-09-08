package com.fundit.auth.infrastructure.identity;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.infrastructure.security.JwtTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * consume()이 실제 Redis에서 원자적 get-and-delete로 동작하는지(1회용 보장) 검증하는 통합 테스트.
 * Testcontainers에 전용 Redis 모듈이 없어(공식 postgresql 모듈과 달리) GenericContainer +
 * 이미지명 기반 @ServiceConnection 자동인식을 사용한다.
 *
 * @SpringBootTest라 Redis뿐 아니라 전체 앱 컨텍스트(JPA/Flyway 등 DataSource 필요한 빈 포함)가
 * 뜬다 — Postgres Testcontainer를 같이 안 띄우면 application-local.yml의 하드코딩된
 * localhost:5432로 접속을 시도하다 연결 거부로 컨텍스트 로딩 자체가 실패한다(실기동으로 확인,
 * 2026-08-31). RefreshTokenJpaRepositoryIntegrationTest와 동일하게 Postgres도 같이 띄운다.
 * member-service.base-url/internal-api.key를 {@code @TestPropertySource}로,
 * RSA 개인키를 {@code @DynamicPropertySource}로 고정하는 이유도 동일 — CI엔 application-local.yml이 없어 이 값들이 미해석 상태로 컨텍스트 로딩이
 * 실패한다(CI에서 재현됨).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "member-service.base-url=http://localhost:8082",
        "internal-api.key=test-only-internal-api-key"
})
class RedisIdentityVerificationStoreIntegrationTest {

    @DynamicPropertySource
    static void jwtKey(DynamicPropertyRegistry registry) {
        JwtTestKeys.register(registry);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private RedisIdentityVerificationStore store;

    @Test
    void 저장한_값을_한번_소비하면_이후_조회는_비어있다() {
        // given
        var identity = new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", LocalDate.of(1999, 1, 1));
        store.save("token-1", identity, Duration.ofMinutes(30));

        // when
        Optional<IdentityVerificationStore.VerifiedIdentity> first = store.consume("token-1");
        Optional<IdentityVerificationStore.VerifiedIdentity> second = store.consume("token-1");

        // then
        assertThat(first).contains(identity);
        assertThat(second).isEmpty();
    }

    @Test
    void 저장하지_않은_토큰을_소비하면_빈값을_반환한다() {
        // when & then
        assertThat(store.consume("no-such-token")).isEmpty();
    }
}
