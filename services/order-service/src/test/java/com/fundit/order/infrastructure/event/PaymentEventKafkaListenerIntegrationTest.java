package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
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
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * payload의 {@code fundingId}는 내부 PK가 아니라 {@code Funding.publicId}(UUID)다 — 이전에는 이
 * 테스트가 Long PK를 보내고 있어 실제 payload와 어긋난 계약을 통과시켰다(#159).
 *
 * <p>payment-service가 발행하는 payment.completed.v1/refund.completed.v1을 실제 Kafka(Testcontainers)로
 * 왕복시켜 {@link PaymentEventKafkaListener} → {@code PaymentEventSyncService}까지 실제로 동작하는지
 * end-to-end로 검증한다({@link RewardEventKafkaListenerIntegrationTest}와 동일한 형태).
 *
 * <p>쿠폰 사용확정/복원 분기(couponIssuanceIds가 채워진 경우)는 {@code PaymentEventSyncServiceUnitTest}가
 * 이미 단위 테스트로 검증하므로, 여기서는 couponIssuanceIds=[]로 보내 "Kafka 토픽 → 리스너 → Funding
 * 상태 반영"이라는 배선 자체만 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class PaymentEventKafkaListenerIntegrationTest {

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
    private FundingRepository fundingRepository;

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

    private Funding givenPendingFunding() {
        Funding funding = Funding.create(
                UUID.randomUUID(), UUID.randomUUID(), "테스트 프로젝트",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(), Instant.now().plusSeconds(3600), null, null, null);
        return fundingRepository.save(funding);
    }

    @Test
    void 결제완료_이벤트를_실제_카프카로_보내면_주문_상태가_결제완료로_전환된다() {
        // given — payment-service PaymentEventTransport가 실제로 보낼 payload 형태
        Funding funding = givenPendingFunding();
        String json = """
                {"eventId":"payment:701","fundingId":"%s","couponIssuanceIds":[]}
                """.formatted(funding.getPublicId());

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.PAYMENT_COMPLETED,
                String.valueOf(funding.getPublicId()), json));
        producer.flush();

        // then — 컨슈머가 비동기로 처리하므로 폴링으로 기다린다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(fundingRepository.findById(funding.getId()))
                        .isPresent()
                        .get()
                        .extracting(Funding::getStatus)
                        .isEqualTo(FundingStatus.FUNDING_IN_PROGRESS));
    }

    @Test
    void 환불완료_이벤트를_실제_카프카로_보내면_주문_상태가_환불완료로_전환된다() {
        // given — 성립 후 하자 전액환불(POST_SUCCESS_DEFECT, fullRefund=true) 케이스
        Funding funding = givenPendingFunding();
        String json = """
                {"eventId":"payment:702","fundingId":"%s","couponIssuanceIds":[],
                 "refundReason":"POST_SUCCESS_DEFECT","fullRefund":true}
                """.formatted(funding.getPublicId());

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.REFUND_COMPLETED,
                String.valueOf(funding.getPublicId()), json));
        producer.flush();

        // then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(fundingRepository.findById(funding.getId()))
                        .isPresent()
                        .get()
                        .extracting(Funding::getStatus)
                        .isEqualTo(FundingStatus.REFUNDED_AFTER_SUCCESS));
    }

    @Test
    void 반품환불_이벤트는_부분환불이어도_주문_상태가_환불완료로_전환된다() {
        // given — 발송 후 구매자 귀책 반품(POST_SUCCESS_RETURN, 반품비 차감이라 fullRefund=false)
        Funding funding = givenPendingFunding();
        String json = """
                {"eventId":"payment:703","fundingId":"%s","couponIssuanceIds":[],
                 "refundReason":"POST_SUCCESS_RETURN","fullRefund":false}
                """.formatted(funding.getPublicId());

        // when
        producer.send(new ProducerRecord<>(KafkaTopics.REFUND_COMPLETED,
                String.valueOf(funding.getPublicId()), json));
        producer.flush();

        // then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(fundingRepository.findById(funding.getId()))
                        .isPresent()
                        .get()
                        .extracting(Funding::getStatus)
                        .isEqualTo(FundingStatus.REFUNDED_AFTER_SUCCESS));
    }
}
