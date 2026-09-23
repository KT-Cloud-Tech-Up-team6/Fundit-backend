package com.fundit.live.infrastructure.seed;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * dev 라이브 목업 50건(판매자별 채널 + 방송)을 기동 시 만든다(이슈 #154).
 *
 * <p><b>dev 전용이다</b>({@code @Profile("dev")}) — local·prod·테스트 컨텍스트에는 빈 자체가 없다.
 * 채널은 {@code sellerId}, 방송은 {@code publicId}로 이미 있으면 건너뛰어 재배포해도 중복되지 않는다.
 *
 * <p>IVS 자원은 가짜 ARN이다(스텁 모드 기준). 채팅방 ARN이 없어 목업 방송은 채팅 토큰 발급 대상이 아니다.
 * {@code sellerId}는 member-service 시더와 같은 고정 UUID라 카드에 판매자명이 붙는다.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class MockLiveSeeder implements ApplicationRunner {

    static final String SEED_FILE = "seed/mock-lives.json";

    private final LiveChannelJpaRepository channelRepository;
    private final LiveSessionJpaRepository sessionRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = seed(read());
            log.info("목업 라이브 시드 완료 — 신규 방송 {}건", created);
        } catch (RuntimeException | IOException e) {
            // 기동은 막지 않는다 — 다음 재시작 때 이미 있는 행은 건너뛰고 나머지만 채운다.
            log.warn("목업 라이브 시드 실패", e);
        }
    }

    int seed(List<MockLive> lives) {
        int created = 0;
        for (MockLive live : lives) {
            LiveChannelJpaEntity channel = channelRepository.findBySellerId(live.sellerId())
                    .orElseGet(() -> channelRepository.save(LiveChannelJpaEntity.builder()
                            .sellerId(live.sellerId())
                            .ivsChannelArn(live.channel().ivsChannelArn())
                            .ivsIngestEndpoint(live.channel().ivsIngestEndpoint())
                            .ivsPlaybackUrl(live.channel().ivsPlaybackUrl())
                            .active(true)
                            .build()));
            MockSession s = live.session();
            if (sessionRepository.findByPublicId(s.publicId()).isPresent()) {
                continue;
            }
            sessionRepository.save(LiveSessionJpaEntity.builder()
                    .publicId(s.publicId())
                    .projectId(s.projectId())
                    .channelId(channel.getId())
                    .status(s.status())
                    .categoryMajor(s.categoryMajor())
                    .categoryMinor(s.categoryMinor())
                    .introText(s.introText())
                    .scheduledStartAt(s.scheduledStartAt())
                    .actualStartAt(s.actualStartAt())
                    .likeCount(s.likeCount())
                    .build());
            created++;
        }
        return created;
    }

    List<MockLive> read() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return jsonMapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    record MockLive(UUID sellerId, MockChannel channel, MockSession session) {
    }

    record MockChannel(String ivsChannelArn, String ivsIngestEndpoint, String ivsPlaybackUrl) {
    }

    record MockSession(UUID publicId, UUID projectId, LiveStatus status, String categoryMajor,
                       String categoryMinor, String introText, Instant scheduledStartAt,
                       Instant actualStartAt, int likeCount) {
    }
}
