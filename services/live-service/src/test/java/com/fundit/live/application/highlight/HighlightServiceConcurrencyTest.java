package com.fundit.live.application.highlight;

import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.SceneLabel;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
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
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 클립 상한은 "세어보고 넣는다"라 <b>검사와 행동 사이가 벌어져 있다.</b>
 * 콜백은 at-least-once라 같은 결과가 두 번 오면 둘 다 countClips=0을 읽고 각각 넣는다 —
 * 세션 행을 잠그지 않으면 방송 1회당 3개 상한이 조용히 깨진다.
 *
 * <p>이건 실제 DB에 동시에 붙여야만 드러난다(test-convention.md 동시성 테스트 기준).
 * Mockito로는 두 트랜잭션이 겹치는 상황 자체를 만들 수 없다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class HighlightServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private HighlightService highlightService;
    @Autowired private LiveSessionJpaRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;
    @Autowired private LiveHighlightJpaRepository highlightRepository;

    private UUID liveId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        highlightRepository.deleteAll();
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(UUID.randomUUID()).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://i").ivsPlaybackUrl("https://p").active(true).build()).getId();
        LiveSessionJpaEntity session = sessionRepository.save(LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID()).projectId(UUID.randomUUID()).channelId(channelId)
                .status(LiveStatus.ENDED).likeCount(0).build());
        liveId = session.getPublicId();
        sessionId = session.getId();
    }

    private List<HighlightService.GeneratedHighlight> twoClips(int base) {
        return List.of(clip(base), clip(base + 100));
    }

    private HighlightService.GeneratedHighlight clip(int startSec) {
        return new HighlightService.GeneratedHighlight(null, HighlightKind.CLIP, SceneLabel.DEMO,
                "실시간 시연", startSec, startSec + 30, "https://clip", "자막",
                GenerationStatus.COMPLETED);
    }

    @Test
    void 콜백이_동시에_여러_번_와도_클립은_3개를_넘지_않는다() throws Exception {
        // given — 같은 방송에 대한 콜백 4개가 한꺼번에 들어온다(재전송·중복 발행)
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        // when — 예외를 삼키지 않는다. 워커가 잠금 대기 타임아웃·데드락으로 전부 실패해도
        // "커밋된 행 3개"가 우연히 맞을 수 있어, 그러면 무엇을 지키는 테스트인지 알 수 없다.
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int base = i * 1000;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                // 상한 초과분은 건너뛰므로 정상 흐름에서 예외가 나지 않는다.
                highlightService.applyGenerated(liveId, twoClips(base));
                done.countDown();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);   // 워커 예외를 테스트로 전파한다
        }
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // then — 잠그지 않았다면 8개까지 들어간다
        assertThat(highlightRepository.countBySessionIdAndKind(sessionId, HighlightKind.CLIP))
                .isEqualTo(3);
    }
}
