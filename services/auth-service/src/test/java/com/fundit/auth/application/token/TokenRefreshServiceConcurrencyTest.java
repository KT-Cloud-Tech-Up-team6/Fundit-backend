package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.persistence.account.AccountJpaEntity;
import com.fundit.auth.infrastructure.persistence.account.AccountJpaRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일한 refresh token으로 동시에 두 요청이 들어왔을 때(탈취된 토큰이 정상 로테이션과 거의
 * 동시에 재사용되는 시나리오) "재사용 탐지 시 전체 세션 무효화" 보장이 실제로 지켜지는지 검증한다.
 *
 * PR 리뷰 지적(2026-09-03): 계정 단위 락(TokenRefreshService.refresh()의 accountRepository.lockForUpdate)
 * 도입 전에는, 재사용을 탐지한 요청의 deleteAllByAccountId가 정상 로테이션한 요청의 새 토큰 저장보다
 * 먼저 커밋되면 그 새 토큰이 무효화를 피해 살아남을 수 있었다. 지금은 락으로 두 요청을 계정 단위로
 * 직렬화해서, 어느 쪽이 이기든 최종적으로 이 계정의 refresh token은 0건이어야 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long!!",
        "jwt.access-token-ttl=30m",
        "jwt.refresh-token-ttl=14d",
        "member-service.base-url=http://localhost:8082"
})
class TokenRefreshServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TokenRefreshService tokenRefreshService;
    @Autowired
    private TokenIssuer tokenIssuer;
    @Autowired
    private AccountJpaRepository accountJpaRepository;
    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Test
    void 같은_refresh_token이_동시에_재사용되면_해당_계정의_모든_토큰이_무효화된다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        accountJpaRepository.save(AccountJpaEntity.builder()
                .id(accountId)
                .email(accountId + "@fundit.com")
                .passwordHash("hash")
                .role("member")
                .build());
        String refreshToken = tokenIssuer.issue(accountId, Role.MEMBER).refreshToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Runnable submitSameToken = () -> {
            ready.countDown();
            try {
                start.await();
                tokenRefreshService.refresh(refreshToken);
                succeeded.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
            }
        };

        // when — 같은 refresh token을 두 스레드에서 동시에 제출한다
        executor.submit(submitSameToken);
        executor.submit(submitSameToken);
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // then — 하나는 로테이션 성공, 하나는 재사용 탐지로 실패하지만
        // 결과적으로 이 계정의 refresh token은 전부 지워져 있어야 한다(핵심 회귀 포인트)
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(1);
        assertThat(refreshTokenJpaRepository.findAll())
                .noneMatch(token -> token.getAccountId().equals(accountId));
    }
}
