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
 * 도메인 쓰기와 아웃박스 적재가 <b>같은 트랜잭션</b>이라는 것을 확인한다(MEMBER-002·005).
 *
 * <p>실패 흐름은 {@code MemberEventOutboxIntegrationExceptionTest}, 실제 Kafka 발행은
 * {@code KafkaMemberEventTransportIntegrationTest}가 본다.
 *
 * <p>poll-interval을 크게 잡아 스케줄러가 끼어들지 않게 하고 워커를 직접 호출한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "member-event-outbox.poll-interval-ms=3600000"})
@Transactional
class MemberEventOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private WishService wishService;
    @Autowired
    private MemberEventOutboxJpaRepository outboxRepository;
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
    void 찜하면_아웃박스에_적재된다() {
        // given
        UUID memberId = createMember();

        // when
        wishService.wish(memberId, 1L);

        // then
        assertThat(unpublished()).singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo(MemberEventOutboxJpaEntity.TYPE_WISHED);
                    assertThat(e.getMemberId()).isEqualTo(memberId);
                    assertThat(e.getProjectId()).isEqualTo(1L);
                });
    }

    @Test
    void 해제하면_해제_이벤트가_적재된다() {
        // given
        UUID memberId = createMember();
        wishService.wish(memberId, 1L);

        // when
        wishService.unwish(memberId, 1L);

        // then
        assertThat(unpublished()).extracting(MemberEventOutboxJpaEntity::getEventType)
                .containsExactly(MemberEventOutboxJpaEntity.TYPE_WISHED, MemberEventOutboxJpaEntity.TYPE_UNWISHED);
    }

    /** 하트 더블탭으로 이벤트가 두 건 쌓이면 아웃박스만 불어난다. */
    @Test
    void 이미_찜한_프로젝트를_다시_찜해도_이벤트는_한_건이다() {
        // given
        UUID memberId = createMember();

        // when
        wishService.wish(memberId, 1L);
        wishService.wish(memberId, 1L);

        // then
        assertThat(unpublished()).hasSize(1);
    }
}
