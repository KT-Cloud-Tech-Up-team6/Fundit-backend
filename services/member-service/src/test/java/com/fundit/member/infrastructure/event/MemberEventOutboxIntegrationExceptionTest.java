package com.fundit.member.infrastructure.event;

import com.fundit.member.application.wish.WishService;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
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
 * 발행이 실패했을 때 아웃박스 행이 어떻게 남는지만 본다. 정상 흐름은
 * {@code MemberEventOutboxIntegrationTest} 참고(test-convention.md 정상/예외 파일 분리).
 *
 * <p><b>브로커를 띄우지 않고 닿지 않는 주소를 준다</b> — 이게 운영에서 실제로 일어나는
 * "브로커 장애" 경로다. Kafka 컨테이너가 필요 없어 이 클래스는 Postgres만 띄운다.
 *
 * <p>여기서 확인하는 것: 전송이 실패하면 행이 <b>발행된 것처럼 지워지지 않는다</b>.
 * 지워지면 찜 통계와 웰컴 쿠폰이 조용히 유실되고 아웃박스를 둔 이유가 사라진다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "member-event-outbox.poll-interval-ms=3600000",
        // 닿지 않는 주소. KafkaProducerConfig의 max.block.ms(5초)가 상한이라 오래 매달리지 않는다.
        "spring.kafka.bootstrap-servers=localhost:1"})
@Transactional
class MemberEventOutboxIntegrationExceptionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private WishService wishService;
    @Autowired
    private MemberEventOutboxJpaRepository outboxRepository;
    @Autowired
    private MemberEventOutboxWorker worker;
    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private UUID createMember() {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build()).getId();
    }

    private List<MemberEventOutboxJpaEntity> unpublished() {
        return outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 50));
    }

    @Test
    void 브로커에_닿지_못하면_미발행으로_남고_시도횟수가_오른다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 1L);

        // when
        worker.publishPending();

        // then
        assertThat(unpublished()).singleElement().satisfies(e -> {
            assertThat(e.getPublishedAt()).isNull();
            assertThat(e.getAttemptCount()).isEqualTo(1);
            assertThat(e.getLastError()).isNotBlank();
        });
    }

    /** 한 건이 실패해도 배치의 나머지가 멈추지 않아야 다음 주기에 함께 재시도된다. */
    @Test
    void 여러_건이_실패해도_모두_미발행으로_남는다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 1L);
        wishService.wish(memberId, 2L);

        // when
        worker.publishPending();

        // then
        assertThat(unpublished()).hasSize(2)
                .allSatisfy(e -> assertThat(e.getAttemptCount()).isEqualTo(1));
    }
}
