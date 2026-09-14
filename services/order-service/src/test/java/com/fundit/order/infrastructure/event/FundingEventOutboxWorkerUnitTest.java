package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingEventOutboxWorkerUnitTest {

    @Mock
    private FundingEventOutboxJpaRepository outboxRepository;
    @Mock
    private FundingEventTransport transport;

    private FundingEventOutboxWorker worker;

    private void setUp() {
        worker = new FundingEventOutboxWorker(outboxRepository, transport, 50);
    }

    private FundingEventOutboxJpaEntity event(String type) {
        return FundingEventOutboxJpaEntity.builder()
                .eventType(type)
                .fundingId(1024L)
                .projectId(123L)
                .build();
    }

    @Test
    void 미달_이벤트_발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        FundingEventOutboxJpaEntity event = event(FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED);
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<FundingGoalFailedEvent> captor = ArgumentCaptor.forClass(FundingGoalFailedEvent.class);
        verify(transport).sendGoalFailed(captor.capture());
        assertThat(captor.getValue().fundingId()).isEqualTo(1024L);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 성립_이벤트_발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        FundingEventOutboxJpaEntity event = event(FundingEventOutboxJpaEntity.TYPE_SUCCEEDED);
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<FundingSucceededEvent> captor = ArgumentCaptor.forClass(FundingSucceededEvent.class);
        verify(transport).sendSucceeded(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(123L);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 취소_이벤트_발행에_성공하면_memberId까지_전달한다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();
        FundingEventOutboxJpaEntity event = FundingEventOutboxJpaEntity.builder()
                .eventType(FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER)
                .fundingId(1024L).projectId(123L).memberId(memberId)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<FundingCancelledByMemberEvent> captor = ArgumentCaptor.forClass(FundingCancelledByMemberEvent.class);
        verify(transport).sendCancelledByMember(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(memberId);
    }

    @Test
    void 발행에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        FundingEventOutboxJpaEntity event = event(FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED);
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 미구성")).when(transport).sendGoalFailed(any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }

    @Test
    void 알_수_없는_이벤트_타입은_실패로_기록한다() {
        // given
        setUp();
        FundingEventOutboxJpaEntity event = event("UnknownType");
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("알 수 없는 펀딩 이벤트 타입");
    }
}
