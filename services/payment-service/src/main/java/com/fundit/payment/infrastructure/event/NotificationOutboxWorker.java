package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundNotificationStatus;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link PaymentNotificationTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (payment-service {@code PaymentEventOutboxWorker}와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "notification-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private final NotificationOutboxJpaRepository outboxRepository;
    private final PaymentNotificationTransport transport;
    private final int batchSize;

    public NotificationOutboxWorker(NotificationOutboxJpaRepository outboxRepository,
                                     PaymentNotificationTransport transport,
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
                transport.sendRefundStatusChanged(new RefundStatusChangedEvent(event.getFundingId(),
                        event.getMemberId(), RefundNotificationStatus.valueOf(event.getStatus())), event.getId());
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("알림 이벤트 발행 실패, 재시도 예정. id={} status={}", event.getId(), event.getStatus(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }
}
