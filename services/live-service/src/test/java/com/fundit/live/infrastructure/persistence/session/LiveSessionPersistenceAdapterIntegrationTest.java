package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인 ↔ JpaEntity 왕복이 필드를 빠뜨리지 않는지 실제 DB로 확인한다.
 * Mapper는 필드를 하나 빠뜨려도 컴파일이 통과하고 단위 테스트에도 안 걸린다 —
 * 실제로 저장했다가 다시 읽어야 드러난다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class LiveSessionPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;
    @Autowired private EntityManager entityManager;

    /**
     * 영속성 컨텍스트를 비우고 DB에서 다시 읽게 한다. 이게 없으면 1차 캐시의 같은 인스턴스가
     * 돌아와 <b>Mapper를 통과하지도, DB 기본값(created_at)을 보지도 못한다</b> —
     * 왕복을 검증한다면서 메모리를 확인하게 된다.
     */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private UUID sellerId;
    private Long channelId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(sellerId)
                .ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://ingest")
                .ivsPlaybackUrl("https://play")
                .active(true)
                .build()).getId();
    }

    @Test
    void 저장했다_읽으면_모든_필드가_그대로다() {
        // given
        UUID projectId = UUID.randomUUID();
        LiveSession session = LiveSession.create(channelId, projectId);
        session.updateSettings("테크·가전", "생활가전", "5분만에 알아보는 신제품",
                "https://img/1.png", Instant.parse("2026-09-10T11:00:00Z"));

        // when
        LiveSession saved = sessionRepository.save(session);
        flushAndClear();
        LiveSession found = sessionRepository.findOwned(saved.getPublicId(), sellerId).orElseThrow();

        // then
        assertThat(found.getProjectId()).isEqualTo(projectId);
        assertThat(found.getChannelId()).isEqualTo(channelId);
        assertThat(found.getCategoryMajor()).isEqualTo("테크·가전");
        assertThat(found.getCategoryMinor()).isEqualTo("생활가전");
        assertThat(found.getIntroText()).isEqualTo("5분만에 알아보는 신제품");
        assertThat(found.getThumbnailUrl()).isEqualTo("https://img/1.png");
        assertThat(found.getStatus()).isEqualTo(LiveStatus.SCHEDULED);
        assertThat(found.getScheduledStartAt()).isEqualTo(Instant.parse("2026-09-10T11:00:00Z"));
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 상태_전이가_그대로_반영된다() {
        // given
        LiveSession saved = sessionRepository.save(LiveSession.create(channelId, UUID.randomUUID()));
        flushAndClear();
        LiveSession loaded = sessionRepository.findOwned(saved.getPublicId(), sellerId).orElseThrow();

        // when
        loaded.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        sessionRepository.save(loaded);
        flushAndClear();
        LiveSession after = sessionRepository.findOwned(saved.getPublicId(), sellerId).orElseThrow();

        // then — status를 ORDINAL로 저장했다면 enum 순서 변경에 조용히 깨진다. STRING 매핑 확인 겸.
        assertThat(after.getStatus()).isEqualTo(LiveStatus.LIVE);
        assertThat(after.getActualStartAt()).isEqualTo(Instant.parse("2026-09-10T11:00:00Z"));
    }

    @Test
    void 송출_실패_흔적도_저장된다() {
        // given
        LiveSession saved = sessionRepository.save(LiveSession.create(channelId, UUID.randomUUID()));
        flushAndClear();
        LiveSession loaded = sessionRepository.findOwned(saved.getPublicId(), sellerId).orElseThrow();

        // when
        loaded.markError("채팅방 생성 실패", Instant.parse("2026-09-10T10:00:00Z"));
        sessionRepository.save(loaded);
        flushAndClear();

        // then
        LiveSession after = sessionRepository.findOwned(saved.getPublicId(), sellerId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LiveStatus.ERROR);
        assertThat(after.getErrorDetail()).isEqualTo("채팅방 생성 실패");
        assertThat(after.getErrorOccurredAt()).isEqualTo(Instant.parse("2026-09-10T10:00:00Z"));
    }
}
