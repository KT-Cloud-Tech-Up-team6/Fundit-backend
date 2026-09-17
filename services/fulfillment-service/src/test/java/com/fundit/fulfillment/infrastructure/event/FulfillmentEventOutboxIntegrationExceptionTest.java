package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발행이 실패했을 때 아웃박스 행이 어떻게 남는지만 본다(member-service
 * {@code MemberEventOutboxIntegrationExceptionTest}와 동일 패턴).
 *
 * <p><b>브로커를 띄우지 않고 닿지 않는 주소를 준다</b> — 이게 운영에서 실제로 일어나는
 * "브로커 장애" 경로다. Kafka 컨테이너가 필요 없어 이 클래스는 Postgres만 띄운다.
 * 발행 서비스(OutboxFulfillmentNotificationPublisher)는 수신자 해석에 외부 클라이언트를 호출하므로,
 * 이 테스트는 그 경로를 우회해 아웃박스 행을 직접 적재한다 — 워커의 {@code deliver()}는
 * 이미 채워진 memberId/relatedPublicId만 사용해 추가 외부 호출이 없다.
 *
 * <p>여기서 확인하는 것: 전송이 실패하면 행이 <b>발행된 것처럼 지워지지 않는다</b>.
 * 지워지면 알림이 조용히 유실되고 아웃박스를 둔 이유가 사라진다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "fulfillment-event-outbox.poll-interval-ms=3600000",
        // 닿지 않는 주소. KafkaConfig의 max.block.ms(5초)가 상한이라 오래 매달리지 않는다.
        "spring.kafka.bootstrap-servers=localhost:1"})
@Transactional
class FulfillmentEventOutboxIntegrationExceptionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FulfillmentEventOutboxJpaRepository outboxRepository;
    @Autowired
    private FulfillmentEventOutboxWorker worker;

    private List<FulfillmentEventOutboxJpaEntity> unpublished() {
        return outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 50));
    }

    @Test
    void 브로커에_닿지_못하면_미발행으로_남고_시도횟수가_오른다() {
        // given
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER)
                .projectId(1L)
                .memberId(UUID.randomUUID())
                .relatedPublicId(UUID.randomUUID())
                .build());

        // when
        worker.publishPending();

        // then
        assertThat(unpublished()).singleElement().satisfies(e -> {
            assertThat(e.getPublishedAt()).isNull();
            assertThat(e.getAttemptCount()).isEqualTo(1);
            assertThat(e.getLastError()).isNotBlank();
        });
    }
}
