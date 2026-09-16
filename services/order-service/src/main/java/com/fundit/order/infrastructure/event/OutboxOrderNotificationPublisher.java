package com.fundit.order.infrastructure.event;

import com.fundit.order.application.notification.OrderNotificationPublisher;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 알림 대상 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link NotificationOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxOrderNotificationPublisher implements OrderNotificationPublisher {

    private final NotificationOutboxJpaRepository outboxRepository;

    @Override
    public void publishRewardRestocked(RewardRestockedEvent event) {
        outboxRepository.save(NotificationOutboxJpaEntity.builder()
                .notifType(NotificationOutboxJpaEntity.TYPE_REWARD_RESTOCK)
                .memberId(event.memberId())
                .rewardId(event.rewardId())
                .build());
    }

    @Override
    public void publishCouponExpiring(CouponExpiringEvent event) {
        outboxRepository.save(NotificationOutboxJpaEntity.builder()
                .notifType(NotificationOutboxJpaEntity.TYPE_COUPON_EXPIRING)
                .memberId(event.memberId())
                .couponIssuanceId(event.couponIssuanceId())
                .build());
    }
}
