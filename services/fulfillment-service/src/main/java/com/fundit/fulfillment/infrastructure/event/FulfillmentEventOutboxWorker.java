package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link FulfillmentNotificationTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (order-service {@code FundingEventOutboxWorker}와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentEventOutboxWorker.class);

    private final FulfillmentEventOutboxJpaRepository outboxRepository;
    private final FulfillmentNotificationTransport transport;
    private final int batchSize;

    public FulfillmentEventOutboxWorker(FulfillmentEventOutboxJpaRepository outboxRepository,
                                         FulfillmentNotificationTransport transport,
                                         @Value("${fulfillment-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${fulfillment-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (FulfillmentEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("알림 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(FulfillmentEventOutboxJpaEntity event) {
        switch (event.getEventType()) {
            case FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER ->
                    transport.sendStaleUpdateReminder(new StaleUpdateReminderEvent(event.getProjectId()));
            case FulfillmentEventOutboxJpaEntity.TYPE_SCHEDULE_CHANGED -> transport.sendScheduleChanged(
                    new ScheduleChangedEvent(event.getProjectId(), FulfillmentStage.valueOf(event.getStage()),
                            ScheduleChangeReasonType.valueOf(event.getReasonType()), event.getNewPlannedDate()));
            case FulfillmentEventOutboxJpaEntity.TYPE_RECEIPT_AUTO_CONFIRMED ->
                    transport.sendReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(event.getFundingId()));
            default -> throw new IllegalStateException("알 수 없는 알림 이벤트 타입: " + event.getEventType());
        }
    }
}
