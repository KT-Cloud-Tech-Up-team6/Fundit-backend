package com.fundit.order.infrastructure.event;

import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaRepository;
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
class NotificationOutboxWorkerUnitTest {

    @Mock
    private NotificationOutboxJpaRepository outboxRepository;
    @Mock
    private OrderNotificationTransport transport;

    private NotificationOutboxWorker worker;

    private void setUp() {
        worker = new NotificationOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 발행에_성공하면_published_at이_채워진다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();
        NotificationOutboxJpaEntity event = NotificationOutboxJpaEntity.builder()
                .notifType(NotificationOutboxJpaEntity.TYPE_REWARD_RESTOCK)
                .memberId(memberId)
                .rewardId(5L)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<RewardRestockedEvent> captor = ArgumentCaptor.forClass(RewardRestockedEvent.class);
        verify(transport).sendRewardRestocked(captor.capture(), any());
        assertThat(captor.getValue().rewardId()).isEqualTo(5L);
        assertThat(captor.getValue().memberId()).isEqualTo(memberId);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        NotificationOutboxJpaEntity event = NotificationOutboxJpaEntity.builder()
                .notifType(NotificationOutboxJpaEntity.TYPE_REWARD_RESTOCK)
                .memberId(UUID.randomUUID())
                .rewardId(5L)
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 오류")).when(transport).sendRewardRestocked(any(), any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 오류");
    }

    @Test
    void 알_수_없는_알림_타입은_실패로_기록한다() {
        // given
        setUp();
        NotificationOutboxJpaEntity event = NotificationOutboxJpaEntity.builder()
                .notifType("UnknownType")
                .memberId(UUID.randomUUID())
                .build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("알 수 없는 알림 이벤트 타입");
    }
}
