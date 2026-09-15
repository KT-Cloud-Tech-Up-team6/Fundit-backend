package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * order-service가 발행하는 funding.cancelled-by-member.v1/funding.goal-failed.v1을 실제
 * Kafka(Testcontainers)로 왕복시켜 {@link FundingLifecycleEventKafkaListener} →
 * {@code FundingLifecycleEventSyncService} → {@code RefundExecutionService}까지 실제로
 * 동작하는지 end-to-end로 검증한다(order-service {@code RewardEventKafkaListenerIntegrationTest}와
 * 동일 패턴). 토스 취소 API는 외부 PG라 목으로 대체한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "payment.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "toss.payments.secret-key=test_sk_dummy"
})
class FundingLifecycleEventKafkaListenerIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

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
    void 참여취소_이벤트를_실제_카프카로_보내면_결제가_전액취소된다() {
        // given — 완료된 결제 하나를 미리 심어둔다
        Long fundingId = 7001L;
        Payment payment = Payment.create(fundingId, UUID.randomUUID(), "fundit-order-cancel-1",
                50_000L, "테스트 주문", null, "idem-cancel-1");
        payment.markCompleted("pay_key_cancel_1", "secret_1", PaymentMethod.CARD,
                null, Instant.now());
        paymentRepository.save(payment);
        when(tossPaymentsClient.cancel(anyString(), anyLong(), anyString()))
                .thenReturn(new TossPaymentsClient.TossCancelResult("txn_1", Instant.now(), 50_000L));

        // when — order-service FundingEventTransport.sendCancelledByMember()가 실제로 보낼 payload 형태
        String json = """
                {"eventId":"order:1","fundingId":%d,"projectId":42,"memberId":"%s"}
                """.formatted(fundingId, payment.getMemberId());
        producer.send(new ProducerRecord<>(KafkaTopics.FUNDING_CANCELLED_BY_MEMBER, String.valueOf(fundingId), json));
        producer.flush();

        // then — 컨슈머가 비동기로 처리하므로 폴링으로 기다린다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(paymentRepository.findById(payment.getId()))
                        .isPresent()
                        .get()
                        .extracting(Payment::getStatus)
                        .isEqualTo(PaymentStatus.CANCELLED));
    }

    @Test
    void 목표미달_이벤트를_실제_카프카로_보내면_결제가_전액취소된다() {
        // given
        Long fundingId = 7002L;
        Payment payment = Payment.create(fundingId, UUID.randomUUID(), "fundit-order-cancel-2",
                30_000L, "테스트 주문", null, "idem-cancel-2");
        payment.markCompleted("pay_key_cancel_2", "secret_2", PaymentMethod.CARD,
                null, Instant.now());
        paymentRepository.save(payment);
        when(tossPaymentsClient.cancel(anyString(), anyLong(), anyString()))
                .thenReturn(new TossPaymentsClient.TossCancelResult("txn_2", Instant.now(), 30_000L));

        // when — order-service FundingEventTransport.sendGoalFailed()가 실제로 보낼 payload 형태
        String json = """
                {"eventId":"order:2","fundingId":%d,"projectId":42}
                """.formatted(fundingId);
        producer.send(new ProducerRecord<>(KafkaTopics.FUNDING_GOAL_FAILED, String.valueOf(fundingId), json));
        producer.flush();

        // then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(paymentRepository.findById(payment.getId()))
                        .isPresent()
                        .get()
                        .extracting(Payment::getStatus)
                        .isEqualTo(PaymentStatus.CANCELLED));
    }

    @Test
    void 알수없는_필드가_섞여있어도_역직렬화가_실패하지_않는다() {
        // given — event-convention.md 6번: 소비자는 모르는 필드를 무시해야 한다.
        Long fundingId = 7003L;
        Payment payment = Payment.create(fundingId, UUID.randomUUID(), "fundit-order-cancel-3",
                20_000L, "테스트 주문", null, "idem-cancel-3");
        payment.markCompleted("pay_key_cancel_3", "secret_3", PaymentMethod.CARD,
                null, Instant.now());
        paymentRepository.save(payment);
        when(tossPaymentsClient.cancel(anyString(), anyLong(), anyString()))
                .thenReturn(new TossPaymentsClient.TossCancelResult("txn_3", Instant.now(), 20_000L));

        String json = """
                {"eventId":"order:3","sourceService":"order","fundingId":%d,"projectId":42,"memberId":"%s"}
                """.formatted(fundingId, payment.getMemberId());
        producer.send(new ProducerRecord<>(KafkaTopics.FUNDING_CANCELLED_BY_MEMBER, String.valueOf(fundingId), json));
        producer.flush();

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(paymentRepository.findById(payment.getId()))
                        .isPresent()
                        .get()
                        .extracting(Payment::getStatus)
                        .isEqualTo(PaymentStatus.CANCELLED));
    }
}
