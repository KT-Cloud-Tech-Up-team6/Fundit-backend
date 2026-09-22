package com.fundit.live.infrastructure.persistence.cuesheet;

import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code failStaleGenerating}의 JPQL 벌크 UPDATE가 실제로 상태·시각 조건을 맞게 거르는지
 * 확인한다 — 목킹으로는 쿼리 조건이 맞는지 알 수 없다(test-convention.md 통합 테스트 기준).
 *
 * <p>{@code updated_at}은 DB 트리거가 매 UPDATE마다 강제로 CURRENT_TIMESTAMP로 덮어써서
 * JPA save로는 과거 시각을 만들 수 없다 — 네이티브 INSERT로 직접 심는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class LiveCueSheetJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LiveCueSheetJpaRepository cueSheetRepository;
    @Autowired private LiveSessionJpaRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;
    @Autowired private EntityManager entityManager;

    private Long channelId;

    @BeforeEach
    void setUp() {
        channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(UUID.randomUUID())
                .ivsChannelArn("arn:" + UUID.randomUUID())
                .ivsIngestEndpoint("rtmps://ingest")
                .ivsPlaybackUrl("https://play")
                .active(true)
                .build()).getId();
    }

    private Long seedSession() {
        return sessionRepository.save(LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .channelId(channelId)
                .status(LiveStatus.DRAFT)
                .likeCount(0)
                .build()).getId();
    }

    private void seedStaleCueSheet(Long sessionId, Instant updatedAt) {
        entityManager.createNativeQuery("""
                        INSERT INTO live_cue_sheets
                            (session_id, mode, status, target_duration_sec, created_at, updated_at)
                        VALUES (?1, 'SCENARIO', 'GENERATING', 600, ?2, ?2)
                        """)
                .setParameter(1, sessionId)
                .setParameter(2, updatedAt)
                .executeUpdate();
    }

    @Test
    void 임계값보다_오래된_GENERATING만_FAILED로_바뀐다() {
        // given
        Long staleSessionId = seedSession();
        Long freshSessionId = seedSession();
        seedStaleCueSheet(staleSessionId, Instant.now().minus(10, ChronoUnit.MINUTES));
        seedStaleCueSheet(freshSessionId, Instant.now());

        // when
        int changed = cueSheetRepository.failStaleGenerating(
                Instant.now().minus(4, ChronoUnit.MINUTES), "AI 응답이 지연되어 실패 처리되었습니다.");
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(changed).isEqualTo(1);
        assertThat(cueSheetRepository.findById(staleSessionId).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.FAILED);
        assertThat(cueSheetRepository.findById(freshSessionId).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.GENERATING);
    }

    @Test
    void COMPLETED는_건드리지_않는다() {
        // given — 이미 끝난 큐시트가 오래됐다고 FAILED로 덮이면 성공한 결과가 사라진다
        Long sessionId = seedSession();
        entityManager.createNativeQuery("""
                        INSERT INTO live_cue_sheets
                            (session_id, mode, status, target_duration_sec, segments, created_at, updated_at)
                        VALUES (?1, 'SCENARIO', 'COMPLETED', 600, '[]', ?2, ?2)
                        """)
                .setParameter(1, sessionId)
                .setParameter(2, Instant.now().minus(1, ChronoUnit.DAYS))
                .executeUpdate();

        // when
        int changed = cueSheetRepository.failStaleGenerating(
                Instant.now().minus(4, ChronoUnit.MINUTES), "사유");
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(changed).isZero();
        assertThat(cueSheetRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.COMPLETED);
    }
}
