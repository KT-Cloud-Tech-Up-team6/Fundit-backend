package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRewardEventPublisherUnitTest {

    @Mock
    private RewardEventOutboxJpaRepository outboxRepository;

    @InjectMocks
    private OutboxRewardEventPublisher publisher;

    @Test
    void 생성_이벤트를_아웃박스에_적재한다() {
        // given
        var event = new RewardEventPublisher.RewardCreatedEvent(10L, 1L, true, 100);

        // when
        publisher.publishRewardCreated(event);

        // then
        ArgumentCaptor<RewardEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(RewardEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        RewardEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(RewardEventOutboxJpaEntity.TYPE_CREATED);
        assertThat(saved.getRewardId()).isEqualTo(10L);
        assertThat(saved.getProjectId()).isEqualTo(1L);
        assertThat(saved.getIsLimited()).isTrue();
        assertThat(saved.getQuantity()).isEqualTo(100);
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void 수정_이벤트를_아웃박스에_적재한다() {
        // given
        var event = new RewardEventPublisher.RewardUpdatedEvent(10L, 1L, false, null);

        // when
        publisher.publishRewardUpdated(event);

        // then
        ArgumentCaptor<RewardEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(RewardEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        RewardEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(RewardEventOutboxJpaEntity.TYPE_UPDATED);
        assertThat(saved.getRewardId()).isEqualTo(10L);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
