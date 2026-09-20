package com.fundit.project.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * member-service가 발행하는 project.wished.v1/project.unwished.v1을 실제 Kafka(Testcontainers)로
 * 왕복시켜 {@link ProjectWishKafkaListener} → {@code ProjectWishStatsEventSubscriber}까지 실제로
 * 동작하는지 end-to-end로 검증한다(order-service {@code RewardEventKafkaListenerIntegrationTest}와 동일 패턴).
 *
 * <p>이전엔 {@code ProjectWishStatsEventSubscriber}가 Spring {@code @EventListener}로만 등록돼 있어
 * 이 토픽의 실제 메시지를 받는 어댑터가 없었다 — 이 테스트가 그 배선 자체를 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProjectWishKafkaListenerIntegrationTest {

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
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectWishStatJpaRepository wishStatJpaRepository;

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

    private Project givenOngoingProject() {
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.ONGOING)
                .goalAmount(1_000_000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    void 찜_이벤트를_실제_카프카로_보내면_찜_건수가_증가한다() {
        // given — member-service KafkaMemberEventTransport.sendWished()가 실제로 보낼 payload 형태
        Project project = givenOngoingProject();
        UUID memberId = UUID.randomUUID();
        String json = """
                {"eventId":"member:901","memberId":"%s","projectId":%d}
                """.formatted(memberId, project.getId());

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.PROJECT_WISHED, memberId.toString(), json));
        producer.flush();

        // then — 컨슈머가 비동기로 처리하므로 폴링으로 기다린다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(wishStatJpaRepository.findById(project.getId()))
                        .isPresent()
                        .get()
                        .extracting(w -> w.getWishCount())
                        .isEqualTo(1));
    }

    @Test
    void 찜해제_이벤트를_실제_카프카로_보내면_찜_건수가_감소한다() {
        // given — 먼저 찜한 상태를 만든 뒤 해제 이벤트를 보낸다
        Project project = givenOngoingProject();
        UUID memberId = UUID.randomUUID();
        String wishedJson = """
                {"eventId":"member:902","memberId":"%s","projectId":%d}
                """.formatted(memberId, project.getId());
        producer.send(new ProducerRecord<>(KafkaTopics.PROJECT_WISHED, memberId.toString(), wishedJson));
        producer.flush();
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(wishStatJpaRepository.findById(project.getId())).isPresent());

        // when
        String unwishedJson = """
                {"eventId":"member:903","memberId":"%s","projectId":%d}
                """.formatted(memberId, project.getId());
        producer.send(new ProducerRecord<>(KafkaTopics.PROJECT_UNWISHED, memberId.toString(), unwishedJson));
        producer.flush();

        // then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(wishStatJpaRepository.findById(project.getId()))
                        .isPresent()
                        .get()
                        .extracting(w -> w.getWishCount())
                        .isEqualTo(0));
    }
}
