package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 펀딩 이벤트가 같은 트랜잭션에서 아웃박스에 남고, 워커가 전송 성공/실패에 따라 published_at을
 * 채우거나 재시도 횟수만 올리는지 검증한다(project-service RewardEventOutboxIntegrationTest와 동일 구성).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class FundingEventOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingEventPublisher fundingEventPublisher;
    @Autowired
    private FundingEventOutboxJpaRepository outboxRepository;

    @Test
    void 목표달성_실패_취소_이벤트를_아웃박스에_남긴다() {
        // when
        fundingEventPublisher.publishFundingGoalFailed(new FundingEventPublisher.FundingGoalFailedEvent(1L, 10L));
        fundingEventPublisher.publishFundingSucceeded(new FundingEventPublisher.FundingSucceededEvent(2L, 10L));
        fundingEventPublisher.publishFundingCancelledByMember(
                new FundingEventPublisher.FundingCancelledByMemberEvent(3L, 10L, UUID.randomUUID()));

        // then
        var unpublished = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(unpublished).hasSize(3);
        assertThat(unpublished).extracting(FundingEventOutboxJpaEntity::getEventType).containsExactly(
                FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED,
                FundingEventOutboxJpaEntity.TYPE_SUCCEEDED,
                FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER);
    }

    @Test
    void 워커가_발행에_성공하면_published_at이_채워진다() {
        // given
        fundingEventPublisher.publishFundingGoalFailed(new FundingEventPublisher.FundingGoalFailedEvent(11L, 10L));
        FundingEventOutboxWorker worker = new FundingEventOutboxWorker(outboxRepository, succeedingTransport(), 50);

        // when
        worker.publishPending();

        // then
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))).isEmpty();
        assertThat(outboxRepository.findAll()).singleElement()
                .extracting(FundingEventOutboxJpaEntity::getPublishedAt).isNotNull();
    }

    @Test
    void 워커가_발행에_실패하면_미발행으로_남기고_재시도횟수를_올린다() {
        // given
        fundingEventPublisher.publishFundingSucceeded(new FundingEventPublisher.FundingSucceededEvent(12L, 10L));
        FundingEventOutboxWorker worker = new FundingEventOutboxWorker(outboxRepository, failingTransport(), 50);

        // when
        worker.publishPending();

        // then
        FundingEventOutboxJpaEntity event = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))
                .getFirst();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    private static FundingEventTransport succeedingTransport() {
        return new FundingEventTransport() {
            @Override
            public void sendGoalFailed(FundingEventPublisher.FundingGoalFailedEvent event) {
            }

            @Override
            public void sendSucceeded(FundingEventPublisher.FundingSucceededEvent event) {
            }

            @Override
            public void sendCancelledByMember(FundingEventPublisher.FundingCancelledByMemberEvent event) {
            }
        };
    }

    private static FundingEventTransport failingTransport() {
        return new FundingEventTransport() {
            @Override
            public void sendGoalFailed(FundingEventPublisher.FundingGoalFailedEvent event) {
                throw new IllegalStateException("브로커 미구성");
            }

            @Override
            public void sendSucceeded(FundingEventPublisher.FundingSucceededEvent event) {
                throw new IllegalStateException("브로커 미구성");
            }

            @Override
            public void sendCancelledByMember(FundingEventPublisher.FundingCancelledByMemberEvent event) {
                throw new IllegalStateException("브로커 미구성");
            }
        };
    }
}
