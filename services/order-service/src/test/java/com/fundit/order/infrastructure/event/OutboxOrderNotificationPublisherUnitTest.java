package com.fundit.order.infrastructure.event;

import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxOrderNotificationPublisherUnitTest {

    @Mock
    private NotificationOutboxJpaRepository outboxRepository;

    @InjectMocks
    private OutboxOrderNotificationPublisher publisher;

    @Test
    void 재입고_알림을_아웃박스에_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        publisher.publishRewardRestocked(new RewardRestockedEvent(5L, memberId));

        // then
        ArgumentCaptor<NotificationOutboxJpaEntity> captor = ArgumentCaptor.forClass(NotificationOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getNotifType()).isEqualTo(NotificationOutboxJpaEntity.TYPE_REWARD_RESTOCK);
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getRewardId()).isEqualTo(5L);
    }

    @Test
    void 쿠폰만료임박_알림을_아웃박스에_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        publisher.publishCouponExpiring(new CouponExpiringEvent(7L, memberId));

        // then
        ArgumentCaptor<NotificationOutboxJpaEntity> captor = ArgumentCaptor.forClass(NotificationOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getNotifType()).isEqualTo(NotificationOutboxJpaEntity.TYPE_COUPON_EXPIRING);
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getCouponIssuanceId()).isEqualTo(7L);
    }
}
