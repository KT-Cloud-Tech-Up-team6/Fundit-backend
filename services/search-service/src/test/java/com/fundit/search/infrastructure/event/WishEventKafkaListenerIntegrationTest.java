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
 * SEARCH-014 구독 경로 + 멱등 가드를 실제 브로커·DB로 검증한다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 처리는 컨슈머 스레드의 별도 트랜잭션에서 일어난다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "search.kafka.retry.interval-ms=50",
        "search.kafka.retry.max-attempts=2"
})
class WishEventKafkaListenerIntegrationTest {

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

    private ProjectDocumentJpaEntity project(long id) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(id)
                .projectPublicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .title("타이틀" + id)
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
                .build();
    }

    private void awaitWishCount(long projectId, int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(projectDocumentJpaRepository.findById(projectId).orElseThrow().getWishCount())
                        .isEqualTo(expected));
    }

    private String payload(long projectId, UUID memberId) {
        return "{\"projectId\":%d,\"memberId\":\"%s\"}".formatted(projectId, memberId);
    }

    @Test
    void 찜_이벤트를_받으면_wish_count가_증가한다() {
        // given
        projectDocumentJpaRepository.save(project(301L));
        UUID memberId = UUID.randomUUID();

        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_WISHED, payload(301L, memberId));

        // then
        awaitWishCount(301L, 1);
    }

    @Test
    void 같은_회원의_찜_이벤트가_재수신돼도_한_번만_반영된다() throws Exception {
        // given — Kafka는 at-least-once라 중복 수신은 예정된 일이다
        projectDocumentJpaRepository.save(project(302L));
        UUID memberId = UUID.randomUUID();

        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_WISHED, payload(302L, memberId)).get();
        kafkaTemplate.send(KafkaTopics.PROJECT_WISHED, payload(302L, memberId)).get();

        // then — search_wish_stat_members UNIQUE(PK)가 두 번째를 무시한다
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(projectDocumentJpaRepository.findById(302L).orElseThrow().getWishCount())
                        .isEqualTo(1));
    }

    @Test
    void 찜_해제_이벤트를_받으면_wish_count가_감소한다() {
        // given
        projectDocumentJpaRepository.save(project(303L));
        UUID memberId = UUID.randomUUID();
        kafkaTemplate.send(KafkaTopics.PROJECT_WISHED, payload(303L, memberId));
        awaitWishCount(303L, 1);

        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_UNWISHED, payload(303L, memberId));

        // then
        awaitWishCount(303L, 0);
    }

    @Test
    void 찜한_적_없는_회원의_해제_이벤트는_카운트를_내리지_않는다() {
        // given
        projectDocumentJpaRepository.save(project(304L));

        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_UNWISHED, payload(304L, UUID.randomUUID()));

        // then — 가드 테이블에 없던 회원이라 delete 영향행이 0, wish_count는 그대로다
        Awaitility.await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(projectDocumentJpaRepository.findById(304L).orElseThrow().getWishCount())
                        .isEqualTo(0));
    }
}
