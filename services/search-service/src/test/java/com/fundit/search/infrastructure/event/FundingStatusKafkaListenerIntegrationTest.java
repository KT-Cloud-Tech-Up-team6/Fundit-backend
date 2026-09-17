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
 * SEARCH-012 구독 경로를 실제 브로커로 검증한다(notification-service
 * NotificationKafkaListenerIntegrationTest와 동일한 이유로 order-service 없이 완결된다).
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 처리는 컨슈머 스레드의 별도 트랜잭션에서 일어난다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FundingStatusKafkaListenerIntegrationTest {

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

    private void awaitStatus(long projectId, ProjectDocumentStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(projectDocumentJpaRepository.findById(projectId).orElseThrow().getStatus())
                        .isEqualTo(expected));
    }

    @Test
    void 펀딩_성립_이벤트를_받으면_SUCCEEDED로_전이한다() {
        // given
        projectDocumentJpaRepository.save(project(101L));

        // when
        kafkaTemplate.send(KafkaTopics.FUNDING_SUCCEEDED, "{\"fundingId\":1,\"projectId\":101}");

        // then
        awaitStatus(101L, ProjectDocumentStatus.SUCCEEDED);
    }

    @Test
    void 펀딩_미달_이벤트를_받으면_FAILED로_전이한다() {
        // given
        projectDocumentJpaRepository.save(project(102L));

        // when
        kafkaTemplate.send(KafkaTopics.FUNDING_GOAL_FAILED, "{\"fundingId\":2,\"projectId\":102}");

        // then
        awaitStatus(102L, ProjectDocumentStatus.FAILED);
    }

    /** 색인이 아직 없는 projectId — 예외 없이 조용히 무시되고(운영 로그만 남음) 컨슈머는 계속 진행한다. */
    @Test
    void 색인에_없는_projectId여도_컨슈머가_멈추지_않는다() {
        // given
        projectDocumentJpaRepository.save(project(201L));

        // when — 없는 프로젝트(999) 먼저, 있는 프로젝트(201) 나중
        kafkaTemplate.send(KafkaTopics.FUNDING_SUCCEEDED, "{\"fundingId\":3,\"projectId\":999}");
        kafkaTemplate.send(KafkaTopics.FUNDING_SUCCEEDED, "{\"fundingId\":4,\"projectId\":201}");

        // then
        awaitStatus(201L, ProjectDocumentStatus.SUCCEEDED);
    }
}
