package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardEventOutboxWorkerUnitTest {

    @Mock
    private RewardEventOutboxJpaRepository outboxRepository;
    @Mock
    private RewardEventTransport transport;

    private RewardEventOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new RewardEventOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 생성_이벤트_발행에_성공하면_published_at을_채운다() {
        // given
        RewardEventOutboxJpaEntity event = RewardEventOutboxJpaEntity.builder()
                .id(1L).eventType(RewardEventOutboxJpaEntity.TYPE_CREATED)
                .rewardId(10L).projectId(1L).isLimited(true).quantity(100).build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any(Pageable.class)))
                .thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        verify(transport).sendCreated(new RewardCreatedEvent(10L, 1L, true, 100));
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttemptCount()).isZero();
    }

    @Test
    void 수정_이벤트_발행에_실패하면_미발행으로_남기고_재시도_횟수를_올린다() {
        // given
        RewardEventOutboxJpaEntity event = RewardEventOutboxJpaEntity.builder()
                .id(2L).eventType(RewardEventOutboxJpaEntity.TYPE_UPDATED)
                .rewardId(10L).projectId(1L).isLimited(false).quantity(null).build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 미구성")).when(transport).sendUpdated(any(RewardUpdatedEvent.class));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }
}
