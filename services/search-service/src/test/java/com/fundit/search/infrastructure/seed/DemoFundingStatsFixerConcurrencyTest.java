package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dev 보정({@link DemoFundingStatsFixer})과 SEARCH-013 이벤트 반영이 겹칠 때 실제 집계값이 목업 수치로
 * 덮이지 않는지 검증한다 — 보정은 "읽어서 0인지 확인 → UPDATE"라 그 사이가 창이고,
 * {@code updateFundingStatsIfUnset}의 {@code current_amount = 0} 조건이 SQL에 있어야만 막힌다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 두 경로가 서로 다른 트랜잭션에서 커밋돼야 재현된다.
 * 순서는 래치로 고정해 매 실행 결과가 같다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class DemoFundingStatsFixerConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 보정이_읽은_뒤_실제_집계가_먼저_커밋되면_실제_값이_남는다() throws Exception {
        // given — 아직 통계가 없는 색인 행(목업 시드 대상)
        UUID publicId = UUID.randomUUID();
        projectDocumentJpaRepository.save(ProjectDocumentJpaEntity.builder()
                .projectId(401L)
                .projectPublicId(publicId)
                .sellerId(UUID.randomUUID())
                .title("경합 대상")
                .categoryMajor("테크·가전")
                .categoryMinor("생활가전")
                .status(ProjectDocumentStatus.ONGOING)
                .goalAmount(1_000_000L)
                .fundingDeadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .projectCreatedAt(Instant.now())
                .currentAmount(0L)
                .achievementRate(0)
                .participantCount(0)
                .wishCount(0)
                .indexedAt(Instant.now())
                .build());

        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch statsCommitted = new CountDownLatch(1);
        AtomicInteger correctedRows = new AtomicInteger(-1);

        // when — 보정이 0을 읽은 상태로 멈춘 사이 SEARCH-013 갱신이 먼저 커밋된다
        Thread fixer = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            long seen = projectDocumentJpaRepository.findByProjectPublicId(publicId).orElseThrow().getCurrentAmount();
            assertThat(seen).isZero();
            read.countDown();
            try {
                statsCommitted.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            correctedRows.set(projectDocumentJpaRepository.updateFundingStatsIfUnset(publicId, 190_000L, 9));
        }));
        fixer.start();

        assertThat(read.await(20, TimeUnit.SECONDS)).isTrue();
        transactionTemplate.executeWithoutResult(status ->
                projectDocumentJpaRepository.updateFundingStats(publicId, 1_850_000L, 7));
        statsCommitted.countDown();
        fixer.join(20_000);

        // then — 보정은 영향 행 0으로 비켜가고 실제 집계값이 남는다
        var document = projectDocumentJpaRepository.findById(401L).orElseThrow();
        assertThat(correctedRows.get()).isZero();
        assertThat(document.getCurrentAmount()).isEqualTo(1_850_000L);
        assertThat(document.getAchievementRate()).isEqualTo(185);
        assertThat(document.getParticipantCount()).isEqualTo(7);
    }
}
