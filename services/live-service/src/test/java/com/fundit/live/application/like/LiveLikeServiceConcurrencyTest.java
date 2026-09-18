package com.fundit.live.application.like;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * like_count는 조건부 UPDATE로 더한다. 조회 후 세팅하는 구현이었다면 동시 요청에서
 * 갱신이 덮어써져 카운트가 어긋난다 — 그건 실제 DB에 동시에 붙여야만 드러난다
 * (test-convention.md 동시성 테스트 기준).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveLikeServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LiveLikeService liveLikeService;
    @Autowired private LiveSessionJpaRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;

    private UUID liveId;

    @BeforeEach
    void setUp() {
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(UUID.randomUUID()).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://i").ivsPlaybackUrl("https://p").active(true).build()).getId();
        liveId = sessionRepository.save(LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID()).projectId(UUID.randomUUID()).channelId(channelId)
                .status(LiveStatus.LIVE).likeCount(0).build()).getPublicId();
    }

    @Test
    void 서로_다른_회원_50명이_동시에_눌러도_카운트가_정확하다() throws Exception {
        // given
        int threads = 50;
        List<UUID> members = java.util.stream.Stream.generate(UUID::randomUUID).limit(threads).toList();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // when
        for (UUID member : members) {
            pool.submit(() -> {
                ready.countDown();
                go.await();
                liveLikeService.like(member, liveId);
                return null;
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(sessionRepository.findByPublicId(liveId).orElseThrow().getLikeCount())
                .isEqualTo(threads);
    }

    @Test
    void 같은_회원이_동시에_여러_번_눌러도_카운트는_1이다() throws Exception {
        // given — PK가 곧 중복 방지 제약이라 ON CONFLICT DO NOTHING이 흡수한다
        UUID member = UUID.randomUUID();
        int threads = 20;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // when
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                go.await();
                try {
                    liveLikeService.like(member, liveId);
                } catch (RuntimeException ignored) {
                    // 동시 삽입 충돌은 정상 — idempotent 결과만 확인한다
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(sessionRepository.findByPublicId(liveId).orElseThrow().getLikeCount())
                .isEqualTo(1);
    }
}
