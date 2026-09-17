package com.fundit.search.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEARCH-011 구독 경로 — 이 서비스의 존재 이유였던 최우선 차단 항목을 실제 브로커·DB로 검증한다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 처리는 컨슈머 스레드의 별도 트랜잭션에서 일어난다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class ProjectIndexEventKafkaListenerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    private String payload(long projectId, String title) {
        UUID publicId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Instant deadline = Instant.now().plus(30, ChronoUnit.DAYS);
        return """
                {"projectId":%d,"publicId":"%s","sellerId":"%s","sellerDisplayName":"프라이팬장인",
                 "title":"%s","thumbnailUrl":"https://cdn.example.com/p/%d/thumb.jpg",
                 "categoryMajor":"테크·가전","categoryMinor":"생활가전","goalAmount":1000000,
                 "fundingStartAt":"%s","fundingDeadline":"%s","createdAt":"%s"}
                """.formatted(projectId, publicId, sellerId, title, projectId,
                Instant.now(), deadline, Instant.now());
    }

    /**
     * findById(...).orElseThrow()를 폴링 안에 두면 안 된다 — 새로 생성되는 행은 첫 폴링 시점에
     * 아직 없는 게 정상인데, orElseThrow()가 던지는 NoSuchElementException은 AssertionError가
     * 아니라서 Awaitility가 재시도하지 않고 그 자리에서 실패로 끝내버린다. Optional을 그대로
     * AssertJ에 넘겨 "없음"도 매 폴링마다 재시도되는 AssertionError로만 표현되게 한다.
     */
    private void awaitTitle(long projectId, String expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    var found = projectDocumentJpaRepository.findById(projectId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getTitle()).isEqualTo(expected);
                });
    }

    @Test
    void 승인_이벤트를_받으면_색인이_새로_생긴다() {
        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_APPROVED, payload(401L, "세상에 없는 프라이팬"));

        // then
        awaitTitle(401L, "세상에 없는 프라이팬");
        assertThat(projectDocumentJpaRepository.findById(401L).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.ONGOING);
    }

    @Test
    void 색인이_아직_없어도_수정_이벤트만으로_새로_생긴다() {
        // given — SEARCH-011 예외처리: 순서 역전(갱신이 승인보다 먼저 도착) 대비
        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_UPDATED, payload(402L, "순서가 뒤바뀐 프로젝트"));

        // then
        awaitTitle(402L, "순서가 뒤바뀐 프로젝트");
    }

    @Test
    void 수정_이벤트는_이미_쌓인_펀딩통계와_찜수를_초기화하지_않는다() {
        // given — SEARCH-012/014가 먼저 반영해둔 펀딩 통계·찜수가 있는 상태
        projectDocumentJpaRepository.save(ProjectDocumentJpaEntity.builder()
                .projectId(403L).projectPublicId(UUID.randomUUID()).sellerId(UUID.randomUUID())
                .title("원래 제목").categoryMajor("테크·가전").categoryMinor("생활가전")
                .status(ProjectDocumentStatus.SUCCEEDED)
                .goalAmount(1_000_000L).fundingDeadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .projectCreatedAt(Instant.now())
                .currentAmount(500_000L).achievementRate(50).participantCount(12).wishCount(7)
                .indexedAt(Instant.now())
                .build());

        // when — 제목만 바뀐 수정 이벤트
        kafkaTemplate.send(KafkaTopics.PROJECT_UPDATED, payload(403L, "제목만 바뀜"));

        // then
        awaitTitle(403L, "제목만 바뀜");
        var updated = projectDocumentJpaRepository.findById(403L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProjectDocumentStatus.SUCCEEDED);
        assertThat(updated.getCurrentAmount()).isEqualTo(500_000L);
        assertThat(updated.getParticipantCount()).isEqualTo(12);
        assertThat(updated.getWishCount()).isEqualTo(7);
    }
}
