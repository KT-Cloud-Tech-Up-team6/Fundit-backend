package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaRepository;
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
 * 펀딩 마감 도래 이벤트가 같은 트랜잭션에서 아웃박스에 남고, 워커가 전송 성공/실패에 따라
 * published_at을 채우거나 재시도 횟수만 올리는지 검증한다({@link RewardEventOutboxIntegrationTest}와 동일 패턴).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class FundingDeadlineEventOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingDeadlinePublisher fundingDeadlinePublisher;
    @Autowired
    private FundingDeadlineEventOutboxJpaRepository outboxRepository;

    @Test
    void 마감_도래_이벤트를_아웃박스에_남긴다() {
        // when
        fundingDeadlinePublisher.publishFundingDeadlineReached(
                new FundingDeadlinePublisher.FundingDeadlineReachedEvent(10L, 5_000_000L));

        // then
        var unpublished = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(unpublished).hasSize(1);
        assertThat(unpublished.getFirst().getProjectId()).isEqualTo(10L);
        assertThat(unpublished.getFirst().getPublishedAt()).isNull();
    }

    @Test
    void 워커가_발행에_성공하면_published_at이_채워진다() {
        // given
        fundingDeadlinePublisher.publishFundingDeadlineReached(
                new FundingDeadlinePublisher.FundingDeadlineReachedEvent(11L, 1_000_000L));
        FundingDeadlineEventOutboxWorker worker = new FundingDeadlineEventOutboxWorker(outboxRepository, succeedingTransport(), 50);

        // when
        worker.publishPending();

        // then
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))).isEmpty();
        assertThat(outboxRepository.findAll())
                .singleElement()
                .extracting(FundingDeadlineEventOutboxJpaEntity::getPublishedAt)
                .isNotNull();
    }

    @Test
    void 워커가_발행에_실패하면_미발행으로_남긴다() {
        // given
        fundingDeadlinePublisher.publishFundingDeadlineReached(
                new FundingDeadlinePublisher.FundingDeadlineReachedEvent(12L, 2_000_000L));
        FundingDeadlineEventOutboxWorker worker = new FundingDeadlineEventOutboxWorker(outboxRepository, failingTransport(), 50);

        // when
        worker.publishPending();

        // then
        FundingDeadlineEventOutboxJpaEntity event = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10))
                .getFirst();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    private static FundingDeadlineEventTransport succeedingTransport() {
        return (event, outboxId) -> { };
    }

    private static FundingDeadlineEventTransport failingTransport() {
        return (event, outboxId) -> {
            throw new IllegalStateException("브로커 미구성");
        };
    }
}
