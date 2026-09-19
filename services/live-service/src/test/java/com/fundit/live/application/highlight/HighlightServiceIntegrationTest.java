package com.fundit.live.application.highlight;

import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.LiveHighlightRepository;
import com.fundit.live.domain.highlight.SceneLabel;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 콜백이 <b>커밋까지 남기는지</b>를 본다.
 *
 * <p>테스트에 {@code @Transactional}을 붙이지 않는 이유가 이 파일의 전부다. 앞서 단위 테스트는
 * {@code verify(save, times(3))}으로 통과했지만, 당시 코드는 네 번째에서 예외를 던져
 * {@code @Transactional}이 <b>앞의 3건을 전부 롤백</b>하고 있었다. 호출 횟수는 커밋을 증명하지 않는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class HighlightServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private HighlightService highlightService;
    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private LiveHighlightRepository highlightRepository;
    @Autowired private LiveHighlightJpaRepository highlightJpaRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;

    private UUID liveId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        highlightJpaRepository.deleteAll();
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(UUID.randomUUID()).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://ingest").ivsPlaybackUrl("https://play")
                .active(true).build()).getId();
        LiveSession session = sessionRepository.save(LiveSession.create(channelId, UUID.randomUUID()));
        liveId = session.getPublicId();
        sessionId = session.getId();
    }

    private HighlightService.GeneratedHighlight clip(int startSec) {
        return new HighlightService.GeneratedHighlight(null, HighlightKind.CLIP, SceneLabel.DEMO,
                "실시간 시연", startSec, startSec + 30, "https://clip", "자막",
                GenerationStatus.COMPLETED);
    }

    @Test
    void 클립이_4개_와도_앞의_3개는_커밋된다() {
        // when — 상한은 3개다(요구사항정의서 6.6.3). 네 번째는 건너뛴다
        highlightService.applyGenerated(liveId, List.of(clip(10), clip(50), clip(90), clip(130)));

        // then — 예외를 던졌다면 여기서 0이 나온다
        assertThat(highlightJpaRepository.countBySessionIdAndKind(sessionId, HighlightKind.CLIP))
                .isEqualTo(3);
    }

    @Test
    void 재생성_결과는_새_행을_만들지_않고_기존_행을_갱신한다() {
        // given — 새 행으로 쌓이면 원래 행이 GENERATING으로 남고 클립 수가 상한에 걸려
        // 재생성 자체가 막힌다
        highlightService.applyGenerated(liveId, List.of(clip(10)));
        UUID highlightId = highlightRepository.findAllBySessionId(sessionId).getFirst().getPublicId();

        // when
        highlightService.applyGenerated(liveId, List.of(new HighlightService.GeneratedHighlight(
                highlightId, HighlightKind.CLIP, SceneLabel.SPEC, "새 제목", 200, 240,
                "https://clip/new", "새 자막", GenerationStatus.COMPLETED)));

        // then
        assertThat(highlightJpaRepository.countBySessionIdAndKind(sessionId, HighlightKind.CLIP))
                .isEqualTo(1);
        LiveHighlight after = highlightRepository.findByPublicId(highlightId).orElseThrow();
        assertThat(after.getTitle()).isEqualTo("새 제목");
        assertThat(after.getStartSec()).isEqualTo(200);
        assertThat(after.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
    }
}
