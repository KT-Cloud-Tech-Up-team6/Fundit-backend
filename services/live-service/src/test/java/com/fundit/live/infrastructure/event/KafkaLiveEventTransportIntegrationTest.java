package com.fundit.live.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.live.application.session.LiveStreamService;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * 발행 경로를 실제 브로커로 검증한다 — 브로커가 없으면 아웃박스가 쌓이기만 하고
 * 아무도 눈치채지 못하므로 "워커가 돌면 토픽에 실제로 들어간다"까지 확인해야 끝난다.
 *
 * <p>Kafka에 @ServiceConnection을 쓰지 않는다 — 그 팩토리는 자동구성 모듈에 있는데
 * 이 서비스는 빈을 직접 만든다. 컨테이너 주소를 KafkaProducerConfig가 읽는 프로퍼티로 넣는다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 워커가 커밋한 결과를 봐야 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        // 스케줄러가 끼어들면 어느 호출이 발행했는지 알 수 없다. 테스트가 직접 워커를 부른다.
        "live.outbox.poll-interval-ms=3600000"})
class KafkaLiveEventTransportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    // 이미지 버전은 Boot BOM의 kafka-clients에 맞춘다 — 3.9.x는 testcontainers-kafka 2.x와
    // 맞지 않아 advertised.listeners 오류로 기동에 실패한다(member-service에서 확인).
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private LiveStreamService liveStreamService;
    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;
    @Autowired private LiveEventOutboxJpaRepository outboxRepository;
    @Autowired private LiveEventOutboxWorker worker;

    private record Fixture(UUID sellerId, UUID liveId) {
    }

    private Fixture seed() {
        UUID sellerId = UUID.randomUUID();
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(sellerId).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://i").ivsPlaybackUrl("https://p").active(true).build()).getId();
        LiveSession saved = sessionRepository.save(LiveSession.create(channelId, UUID.randomUUID()));
        return new Fixture(sellerId, saved.getPublicId());
    }

    private List<ConsumerRecord<String, String>> drain(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofSeconds(20)).forEach(records::add);
        }
        return records;
    }

    @Test
    void 방송을_종료하면_live_ended가_토픽에_실제로_들어간다() {
        // given
        Fixture f = seed();
        liveStreamService.start(f.sellerId(), f.liveId());
        liveStreamService.end(f.sellerId(), f.liveId());

        // when
        worker.publishPending();

        // then
        ConsumerRecord<String, String> record = drain(KafkaTopics.LIVE_ENDED).stream()
                .filter(r -> r.value().contains(f.liveId().toString()))
                .findFirst().orElseThrow(() -> new AssertionError("live.ended.v1에 메시지가 없다"));

        // 파티션 키가 liveId여야 한 방송의 시작·종료·요약 순서가 뒤집히지 않는다
        assertThat(record.key()).isEqualTo(f.liveId().toString());
        // 봉투 없는 평평한 JSON + eventId = "live:{outboxId}"(event-convention.md 4·5번)
        assertThat(record.value())
                .contains("\"liveId\":\"" + f.liveId() + "\"")
                .contains("\"eventId\":\"live:");
    }

    @Test
    void 방송을_시작하면_live_started가_발행된다() {
        // given — notification이 이걸 구독해 신청자에게 시작 알림을 만든다.
        // live는 신청자 목록을 모르므로 notification.raised.v1을 직접 쏠 수 없다.
        Fixture f = seed();
        liveStreamService.start(f.sellerId(), f.liveId());

        // when
        worker.publishPending();

        // then
        assertThat(drain(KafkaTopics.LIVE_STARTED))
                .anyMatch(r -> r.value().contains(f.liveId().toString()));
    }

    @Test
    void 발행에_성공한_행만_published_at이_채워진다() {
        // given
        Fixture f = seed();
        liveStreamService.start(f.sellerId(), f.liveId());

        // when
        worker.publishPending();

        // then — 미발행으로 남는 행이 없어야 한다(브로커가 살아 있는 경우)
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                org.springframework.data.domain.PageRequest.of(0, 50))).isEmpty();
    }
}
