package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxFundingEventPublisherUnitTest {

    @Mock
    private FundingEventOutboxJpaRepository outboxRepository;

    private OutboxFundingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxFundingEventPublisher(outboxRepository);
    }

    @Test
    void 미달_이벤트를_아웃박스에_적재한다() {
        // when
        publisher.publishFundingGoalFailed(new FundingGoalFailedEvent(1024L, 123L));

        // then
        ArgumentCaptor<FundingEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FundingEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED);
        assertThat(captor.getValue().getFundingId()).isEqualTo(1024L);
        assertThat(captor.getValue().getProjectId()).isEqualTo(123L);
    }

    @Test
    void 성립_이벤트를_아웃박스에_적재한다() {
        // when
        publisher.publishFundingSucceeded(new FundingSucceededEvent(1024L, 123L));

        // then
        ArgumentCaptor<FundingEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FundingEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FundingEventOutboxJpaEntity.TYPE_SUCCEEDED);
    }

    @Test
    void 취소_이벤트는_memberId까지_함께_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        publisher.publishFundingCancelledByMember(new FundingCancelledByMemberEvent(1024L, 123L, memberId));

        // then
        ArgumentCaptor<FundingEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FundingEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER);
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
    }
}
