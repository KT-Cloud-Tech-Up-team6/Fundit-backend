package com.fundit.order.infrastructure.event;

import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link OrderNotificationTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (order-service {@code FundingEventOutboxWorker}와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "notification-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private final NotificationOutboxJpaRepository outboxRepository;
    private final OrderNotificationTransport transport;
    private final int batchSize;

    public NotificationOutboxWorker(NotificationOutboxJpaRepository outboxRepository,
                                     OrderNotificationTransport transport,
                                     @Value("${notification-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (NotificationOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("알림 이벤트 발행 실패, 재시도 예정. id={} notifType={}", event.getId(), event.getNotifType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(NotificationOutboxJpaEntity event) {
        switch (event.getNotifType()) {
            case NotificationOutboxJpaEntity.TYPE_REWARD_RESTOCK -> transport.sendRewardRestocked(
                    new RewardRestockedEvent(event.getRewardId(), event.getMemberId()), event.getId());
            case NotificationOutboxJpaEntity.TYPE_COUPON_EXPIRING -> transport.sendCouponExpiring(
                    new CouponExpiringEvent(event.getCouponIssuanceId(), event.getMemberId()), event.getId());
            default -> throw new IllegalStateException("알 수 없는 알림 이벤트 타입: " + event.getNotifType());
        }
    }
}
