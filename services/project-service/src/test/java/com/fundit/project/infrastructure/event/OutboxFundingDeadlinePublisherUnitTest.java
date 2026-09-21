package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxFundingDeadlinePublisherUnitTest {

    @Mock
    private FundingDeadlineEventOutboxJpaRepository outboxRepository;

    @InjectMocks
    private OutboxFundingDeadlinePublisher publisher;

    @Test
    void 마감_도래_이벤트를_아웃박스에_적재한다() {
        // given
        var event = new FundingDeadlinePublisher.FundingDeadlineReachedEvent(1L, 5_000_000L);

        // when
        publisher.publishFundingDeadlineReached(event);

        // then
        ArgumentCaptor<FundingDeadlineEventOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(FundingDeadlineEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        FundingDeadlineEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(1L);
        assertThat(saved.getGoalAmount()).isEqualTo(5_000_000L);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
