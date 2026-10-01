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
 * SEARCH-013 구독 경로를 실제 브로커로 검증한다(order-service 없이 완결된다 —
 * FundingStatusKafkaListenerIntegrationTest와 같은 이유).
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
class FundingRewardStatsKafkaListenerIntegrationTest {

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

    private ProjectDocumentJpaEntity project(long id, UUID publicId) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(id)
                .projectPublicId(publicId)
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

    @Test
    void 리워드_통계_이벤트를_받으면_모금액_달성률_참여자수를_갱신한다() {
        // given
        UUID publicId = UUID.randomUUID();
        projectDocumentJpaRepository.save(project(301L, publicId));

        // when — 옵션 행은 리워드 행과 같은 금액을 쪼갠 것이라 모금액에 더해지지 않아야 한다
        kafkaTemplate.send(KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED, """
                {"projectId":"%s","participantCount":7,"rewardStats":[
                  {"rewardId":1,"optionValueId":null,"purchasedQuantity":3,"purchasedAmount":1850000},
                  {"rewardId":1,"optionValueId":11,"purchasedQuantity":3,"purchasedAmount":1850000}]}
                """.formatted(publicId));

        // then
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var document = projectDocumentJpaRepository.findById(301L).orElseThrow();
            assertThat(document.getCurrentAmount()).isEqualTo(1_850_000L);
            assertThat(document.getAchievementRate()).isEqualTo(185);
            assertThat(document.getParticipantCount()).isEqualTo(7);
            assertThat(document.getFundingStatsSyncedAt()).isNotNull();
        });
    }

    /** 색인에 없는 publicId는 건너뛴다 — 재시도·DLT 없이 같은 파티션의 다음 메시지가 바로 처리된다. */
    @Test
    void 색인에_없는_publicId여도_컨슈머가_멈추지_않는다() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();
        projectDocumentJpaRepository.save(project(302L, publicId));
        String key = publicId.toString();

        // when
        kafkaTemplate.send(KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED, key,
                "{\"projectId\":\"%s\",\"participantCount\":1,\"rewardStats\":[]}".formatted(UUID.randomUUID())).get();
        kafkaTemplate.send(KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED, key,
                "{\"projectId\":\"%s\",\"participantCount\":2,\"rewardStats\":[{\"rewardId\":1,\"purchasedQuantity\":1,\"purchasedAmount\":500000}]}"
                        .formatted(publicId));

        // then
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(projectDocumentJpaRepository.findById(302L).orElseThrow().getCurrentAmount())
                        .isEqualTo(500_000L));
    }
}
