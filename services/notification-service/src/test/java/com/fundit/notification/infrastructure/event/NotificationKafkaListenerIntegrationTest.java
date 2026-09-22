package com.fundit.notification.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaEntity;
import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaRepository;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaEntity;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTI-006 구독 경로를 실제 브로커로 검증한다.
 *
 * <p><b>발행 측 서비스 없이 완결된다</b> — 테스트가 토픽에 직접 메시지를 넣는다.
 * order/payment/fulfillment가 아직 memberId를 싣지 않는 상태여도 이 경로는 여기서 다 검증된다.
 *
 * <p>브로커·DB 모두 Testcontainers로 띄우고 {@code @ServiceConnection}으로 접속 정보를 주입받는다
 * (Kafka는 spring-boot-kafka의 ApacheKafkaContainerConnectionDetailsFactory가 처리한다).
 * CI에 서비스 컨테이너를 따로 띄우지 않는 이유는 {@code docs/ci-workflow-guide.md} 참고.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 적재는 컨슈머 스레드의 별도 트랜잭션에서 일어나므로
 * 테스트 트랜잭션에 묶으면 결과가 보이지 않는다. 그래서 각 테스트가 자기 memberId를 쓴다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class NotificationKafkaListenerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    // 이미지 버전은 Boot BOM이 가져오는 kafka-clients(4.2.1)에 맞춘다.
    // 3.9.x 이미지는 testcontainers-kafka 2.0.5와 맞지 않아
    // "advertised.listeners cannot use the nonroutable meta-address 0.0.0.0"로 기동에 실패한다.
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private NotificationJpaRepository notificationJpaRepository;
    @Autowired
    private NotificationSettingJpaRepository notificationSettingJpaRepository;
    @Autowired
    private LiveNotifyRequestJpaRepository liveNotifyRequestJpaRepository;

    private static String payload(String eventId, UUID memberId, String notifType) {
        return """
                {"eventId":"%s","memberId":"%s","notifType":"%s",
                 "title":"「무선 이어폰 프로젝트」 배송이 '출고' 단계로 넘어갔어요",
                 "relatedUrl":"/my/fundings/1/shipping"}
                """.formatted(eventId, memberId, notifType);
    }

    private static String liveStartedPayload(String eventId, UUID liveId, String projectTitle) {
        return """
                {"eventId":"%s","liveId":"%s","projectId":"%s","startedAt":"2026-09-22T10:00:00Z",
                 "projectTitle":"%s"}
                """.formatted(eventId, liveId, UUID.randomUUID(), projectTitle);
    }

    private void send(String body) {
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_RAISED, body);
    }

    private void sendLiveStarted(String body) {
        kafkaTemplate.send(KafkaTopics.LIVE_STARTED, body);
    }

    private void awaitUnreadCount(UUID memberId, long expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(
                        notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId)).isEqualTo(expected));
    }

    @Test
    void 토픽에_들어온_알림이_적재된다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        send(payload("order:1", memberId, "SHIPPING_UPDATE"));

        // then
        awaitUnreadCount(memberId, 1);
    }

    @Test
    void 같은_eventId가_재전송되어도_한_건만_적재된다() {
        // given — Kafka는 at-least-once라 중복 수신은 예정된 일이다
        UUID memberId = UUID.randomUUID();

        // when
        send(payload("order:2", memberId, "SHIPPING_UPDATE"));
        send(payload("order:2", memberId, "SHIPPING_UPDATE"));

        // then — (event_id, member_id) UNIQUE가 두 번째를 무시한다
        awaitUnreadCount(memberId, 1);
        Awaitility.await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(
                        notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId)).isEqualTo(1));
    }

    @Test
    void 수신_거부한_유형은_적재되지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        // insertIgnoringConflict는 @Modifying 네이티브 쿼리라 호출부 트랜잭션을 요구하는데
        // 이 테스트는 (컨슈머 스레드를 보려고) 비트랜잭션이다. save()는 SimpleJpaRepository가
        // 자체 트랜잭션을 걸어주므로 준비 데이터 적재엔 이쪽을 쓴다.
        notificationSettingJpaRepository.save(NotificationSettingJpaEntity.builder()
                .memberId(memberId).notifType(NotifType.SHIPPING_UPDATE).build());

        // when — 거부한 유형 먼저, 허용된 유형을 뒤에 보내 "처리가 끝났음"을 관측 가능하게 한다
        send(payload("order:3", memberId, "SHIPPING_UPDATE"));
        send(payload("order:4", memberId, "LIVE_START"));

        // then — 뒤에 보낸 것만 남는다(거부는 실패가 아니라 "만들지 않음")
        awaitUnreadCount(memberId, 1);
    }

    /**
     * event-convention.md 6번 — "소비자는 모르는 필드를 무시해야 한다".
     * 이게 지켜져야 발행 측이 필드를 추가할 때(버전 유지) 구독자가 안 깨진다.
     * 규약에 적어만 두고 검증한 적이 없어 여기서 고정한다.
     */
    @Test
    void 모르는_필드가_있어도_적재된다() {
        // given — 발행 측이 .v1 유지한 채 필드를 추가한 상황
        UUID memberId = UUID.randomUUID();
        String withExtraField = """
                {"eventId":"order:7","memberId":"%s","notifType":"SHIPPING_UPDATE",
                 "title":"제목","relatedUrl":"/x","traceId":"나중에 추가된 필드","attempt":3}
                """.formatted(memberId);

        // when
        send(withExtraField);

        // then
        awaitUnreadCount(memberId, 1);
    }

    /** NOTI-006 핵심 — 한 건 때문에 파티션이 멈추면 뒤의 알림이 전부 막힌다. */
    @Test
    void 깨진_메시지가_와도_컨슈머가_멈추지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        send("{ 이건 JSON이 아니다");
        send(payload("order:5", memberId, "NOT_A_REAL_TYPE"));   // 정의되지 않은 notifType
        send(payload("order:6", memberId, "SHIPPING_UPDATE"));   // 정상

        // then — 앞의 두 건은 건너뛰고 정상 메시지는 처리된다
        awaitUnreadCount(memberId, 1);
    }

    /** NOTI-002 — live.started.v1을 받아 신청자 목록을 직접 조회해 팬아웃하는지 실제 브로커로 확인한다. */
    @Test
    void 라이브가_시작되면_신청자에게_알림이_적재된다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        liveNotifyRequestJpaRepository.save(
                LiveNotifyRequestJpaEntity.builder().liveId(liveId).memberId(memberId).build());

        // when
        sendLiveStarted(liveStartedPayload("live:1", liveId, "무선 이어폰"));

        // then
        awaitUnreadCount(memberId, 1);
    }

    @Test
    void 신청하지_않은_회원은_라이브_시작_알림을_받지_않는다() {
        // given — 신청 행이 없는 liveId
        UUID liveId = UUID.randomUUID();
        UUID unrelatedMember = UUID.randomUUID();

        // when
        sendLiveStarted(liveStartedPayload("live:2", liveId, "무선 이어폰"));

        // then — 아무도 신청 안 했으니 적재도 없다(예외 없이 정상 종료했는지가 핵심)
        Awaitility.await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(
                        notificationJpaRepository.countByMemberIdAndReadAtIsNull(unrelatedMember)).isZero());
    }
}
