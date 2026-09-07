package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리워드 수량 이벤트가 같은 트랜잭션에서 아웃박스에 남고, 워커가 전송 성공/실패에 따라
 * published_at을 채우거나 재시도 횟수만 올리는지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RewardEventOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RewardEventPublisher rewardEventPublisher;
    @Autowired
    private RewardEventOutboxJpaRepository outboxRepository;

    @Test
    void 생성과_수정_이벤트를_아웃박스에_남긴다() {
        // when
        rewardEventPublisher.publishRewardCreated(
                new RewardEventPublisher.RewardCreatedEvent(10L, 1L, true, 100));
        rewardEventPublisher.publishRewardUpdated(
                new RewardEventPublisher.RewardUpdatedEvent(10L, 1L, true, 80));

        // then
        var unpublished = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(unpublished).hasSize(2);
        assertThat(unpublished).extracting(RewardEventOutboxJpaEntity::getEventType)
                .containsExactly(RewardEventOutboxJpaEntity.TYPE_CREATED, RewardEventOutboxJpaEntity.TYPE_UPDATED);
        assertThat(unpublished).allMatch(event -> event.getPublishedAt() == null);
    }

    @Test
    void 워커가_발행에_성공하면_published_at이_채워진다() {
        // given
        rewardEventPublisher.publishRewardCreated(
                new RewardEventPublisher.RewardCreatedEvent(11L, 1L, true, 50));
        RewardEventOutboxWorker worker = new RewardEventOutboxWorker(outboxRepository, succeedingTransport(), 50);

        // when
        worker.publishPending();

        // then
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))).isEmpty();
        assertThat(outboxRepository.findAll())
                .singleElement()
                .extracting(RewardEventOutboxJpaEntity::getPublishedAt)
                .isNotNull();
    }

    @Test
    void 워커가_발행에_실패하면_미발행으로_남긴다() {
        // given
        rewardEventPublisher.publishRewardUpdated(
                new RewardEventPublisher.RewardUpdatedEvent(12L, 1L, false, null));
        RewardEventOutboxWorker worker = new RewardEventOutboxWorker(outboxRepository, failingTransport(), 50);

        // when
        worker.publishPending();

        // then
        RewardEventOutboxJpaEntity event = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))
                .getFirst();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    private static RewardEventTransport succeedingTransport() {
        return new RewardEventTransport() {
            @Override
            public void sendCreated(RewardEventPublisher.RewardCreatedEvent event) {
            }

            @Override
            public void sendUpdated(RewardEventPublisher.RewardUpdatedEvent event) {
            }
        };
    }

    private static RewardEventTransport failingTransport() {
        return new RewardEventTransport() {
            @Override
            public void sendCreated(RewardEventPublisher.RewardCreatedEvent event) {
                throw new IllegalStateException("브로커 미구성");
            }

            @Override
            public void sendUpdated(RewardEventPublisher.RewardUpdatedEvent event) {
                throw new IllegalStateException("브로커 미구성");
            }
        };
    }
}
