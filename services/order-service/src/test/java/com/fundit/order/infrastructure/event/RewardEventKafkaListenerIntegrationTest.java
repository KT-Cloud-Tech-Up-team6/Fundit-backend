package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.infrastructure.persistence.inventory.InventoryJpaRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * project-service가 발행하는 reward.created.v1을 실제 Kafka(Testcontainers)로 왕복시켜
 * {@link RewardEventKafkaListener} → {@code RewardStockSyncService}까지 실제로 동작하는지
 * end-to-end로 검증한다(event-convention.md 배선의 대표 검증 케이스 — 나머지 Tier A/B 경로도
 * 동일한 형태로 각 서비스에 추가한다).
 *
 * <p>project-service의 실제 Producer 코드를 직접 쓰지 않고 그 결과물과 동일한 형태의 raw JSON을
 * 원시 {@link KafkaProducer}로 보낸다 — 이벤트 레코드/발행 코드를 서비스 간에 공유하지 않는다는
 * event-convention.md 4번 원칙과 일관되게, 소비 측 테스트는 "발행 측이 이런 JSON을 보낼 것"이라는
 * 계약만 안다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RewardEventKafkaListenerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    private static KafkaProducer<String, String> producer;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @BeforeAll
    static void setUpProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    @AfterAll
    static void tearDownProducer() {
        producer.close();
    }

    @Test
    void 리워드_생성_이벤트를_실제_카프카로_보내면_재고_원장이_생성된다() {
        // given — project-service RewardEventTransport.sendCreated()가 실제로 보낼 payload 형태
        Long rewardId = 9001L;
        String json = """
                {"eventId":"project:501","rewardId":%d,"projectId":42,"isLimited":true,"quantity":30}
                """.formatted(rewardId);

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.REWARD_CREATED, String.valueOf(rewardId), json));
        producer.flush();

        // then — 컨슈머가 비동기로 처리하므로 폴링으로 기다린다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(inventoryJpaRepository.findByRewardId(rewardId))
                        .isPresent()
                        .get()
                        .satisfies(inventory -> {
                            assertThat(inventory.getAvailableStock()).isEqualTo(30);
                            assertThat(inventory.getInitialQuantity()).isEqualTo(30);
                        }));
    }

    @Test
    void 모르는_필드가_섞여있어도_역직렬화가_실패하지_않는다() {
        // given — event-convention.md 6번: 소비자는 모르는 필드를 무시해야 한다.
        // eventId는 RewardCreatedEvent에 없는 필드이고, 발행 측이 앞으로 필드를 추가해도
        // (예: sourceService) 이 컨슈머가 깨지면 안 된다는 걸 같이 검증한다.
        Long rewardId = 9002L;
        String json = """
                {"eventId":"project:502","sourceService":"project","rewardId":%d,
                 "projectId":42,"isLimited":true,"quantity":10}
                """.formatted(rewardId);

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.REWARD_CREATED, String.valueOf(rewardId), json));
        producer.flush();

        // then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(inventoryJpaRepository.findByRewardId(rewardId)).isPresent());
    }
}
