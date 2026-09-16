package com.fundit.member.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.member.application.wish.WishService;
import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발행 경로를 실제 브로커로 검증한다(MEMBER-005) — 소비 측 서비스 없이 완결된다.
 *
 * <p>브로커가 없으면 아웃박스가 쌓이기만 하고 아무도 그걸 눈치채지 못하므로,
 * "워커가 돌면 토픽에 실제로 들어간다"까지 확인해야 이 기능이 끝난다.
 *
 * <p>@ServiceConnection을 Kafka에 쓰지 않는다 — 그 팩토리는 spring-boot-kafka(자동구성 모듈)에
 * 들어 있는데 이 서비스는 빈을 직접 만들어 쓰므로 스타터를 넣지 않았다. 대신 컨테이너 주소를
 * KafkaProducerConfig가 읽는 프로퍼티로 직접 주입한다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 워커가 커밋한 결과를 봐야 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "wish-event-outbox.poll-interval-ms=3600000"})
class KafkaWishEventTransportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    // 이미지 버전은 Boot BOM의 kafka-clients(4.2.1)에 맞춘다 — 3.9.x는 testcontainers-kafka 2.x와
    // 맞지 않아 "advertised.listeners cannot use the nonroutable meta-address 0.0.0.0"으로 기동에 실패한다.
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private WishService wishService;
    @Autowired
    private WishEventOutboxJpaRepository outboxRepository;
    @Autowired
    private WishEventOutboxWorker worker;
    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private UUID createMember() {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build()).getId();
    }

    /** 토픽 하나를 처음부터 읽는다. 매번 새 group-id라 이전 테스트의 커밋 오프셋에 영향받지 않는다. */
    private List<ConsumerRecord<String, String>> drain(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(20));
            polled.forEach(records::add);
        }
        return records;
    }

    @Test
    void 워커가_돌면_찜_이벤트가_토픽에_실제로_들어간다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 42L);

        // when
        worker.publishPending();

        // then
        ConsumerRecord<String, String> record = drain(KafkaTopics.PROJECT_WISHED).stream()
                .filter(r -> r.value().contains(memberId.toString()))
                .findFirst().orElseThrow(() -> new AssertionError("project.wished.v1에 메시지가 없다"));

        // 파티션 키가 memberId여야 한 회원의 찜→해제 순서가 보장된다
        assertThat(record.key()).isEqualTo(memberId.toString());
        // 봉투 없는 평평한 JSON + eventId = "member:{outboxId}" (event-convention.md 4·5번)
        assertThat(record.value())
                .contains("\"memberId\":\"" + memberId + "\"")
                .contains("\"projectId\":42")
                .containsPattern("\"eventId\":\"member:\\d+\"")
                .doesNotContain("payload");
    }

    @Test
    void 해제_이벤트는_별도_토픽으로_나간다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 43L);
        wishService.unwish(memberId, 43L);

        // when
        worker.publishPending();

        // then
        assertThat(drain(KafkaTopics.PROJECT_UNWISHED))
                .anySatisfy(r -> assertThat(r.value()).contains(memberId.toString()));
    }

    /** 발행에 성공한 행이 미발행으로 남으면 워커가 같은 메시지를 영원히 다시 보낸다. */
    @Test
    void 발행에_성공하면_published_at이_채워진다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 44L);

        // when
        worker.publishPending();

        // then
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 50)))
                .extracting(WishEventOutboxJpaEntity::getMemberId)
                .doesNotContain(memberId);
    }
}
