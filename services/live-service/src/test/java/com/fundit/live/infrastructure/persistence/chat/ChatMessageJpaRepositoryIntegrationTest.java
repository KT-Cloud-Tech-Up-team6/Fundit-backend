package com.fundit.live.infrastructure.persistence.chat;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ON CONFLICT DO NOTHING은 실제 DB 제약이 있어야 동작한다 — 목킹으로는 확인할 수 없다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class ChatMessageJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private ChatMessageJpaRepository chatMessageRepository;
    @Autowired private LiveSessionJpaRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;

    private Long sessionId;

    @BeforeEach
    void setUp() {
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(UUID.randomUUID()).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://i").ivsPlaybackUrl("https://p").active(true).build()).getId();
        sessionId = sessionRepository.save(LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID()).projectId(UUID.randomUUID()).channelId(channelId)
                .status(LiveStatus.LIVE).likeCount(0).build()).getId();
    }

    @Test
    void 같은_메시지를_두_번_받아도_한_행만_남는다() {
        // given — Firehose는 재전송이 가능하다
        Instant sentAt = Instant.parse("2026-09-10T11:00:00Z");
        UUID sender = UUID.randomUUID();

        // when
        int first = chatMessageRepository.insertIgnoringConflict("msg-1", sessionId, sender, "안녕하세요", sentAt);
        int second = chatMessageRepository.insertIgnoringConflict("msg-1", sessionId, sender, "안녕하세요", sentAt);

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void 다시보기_채팅은_구간으로_조회한다() {
        // given — 시점 1개씩 왕복하면 요청 수가 방송 길이만큼 늘어난다
        UUID sender = UUID.randomUUID();
        chatMessageRepository.insertIgnoringConflict("m1", sessionId, sender, "시작", Instant.parse("2026-09-10T11:00:00Z"));
        chatMessageRepository.insertIgnoringConflict("m2", sessionId, sender, "중간", Instant.parse("2026-09-10T11:05:00Z"));
        chatMessageRepository.insertIgnoringConflict("m3", sessionId, sender, "끝", Instant.parse("2026-09-10T11:20:00Z"));

        // when
        var found = chatMessageRepository.findBySessionIdAndSentAtBetweenOrderBySentAtAsc(
                sessionId, Instant.parse("2026-09-10T11:00:00Z"), Instant.parse("2026-09-10T11:10:00Z"));

        // then
        assertThat(found).hasSize(2).extracting(ChatMessageJpaEntity::getContent)
                .containsExactly("시작", "중간");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_밖에서_불러도_전송_완료가_저장된다() {
        // given — 발송 스케줄러는 AI 호출 때문에 트랜잭션 없이 부른다. readOnly 트랜잭션을
        // 물려받으면 UPDATE가 실패해 같은 댓글이 계속 재전송된다
        Long id = chatMessageRepository.save(ChatMessageJpaEntity.builder()
                .ivsMessageId("sent-1").sessionId(sessionId).senderId(UUID.randomUUID())
                .content("질문").sentAt(Instant.parse("2026-09-10T11:00:00Z")).build()).getId();
        try {
            // when
            chatMessageRepository.markSentToAi(java.util.List.of(id), Instant.parse("2026-09-10T11:00:03Z"));

            // then
            assertThat(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(sessionId))
                    .isEmpty();
        } finally {
            // 이 테스트만 커밋되므로 다른 테스트의 count()에 섞이지 않게 지운다
            chatMessageRepository.deleteAllInBatch();
        }
    }
}
