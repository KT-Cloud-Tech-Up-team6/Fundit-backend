package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 알림 대상 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link FulfillmentEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxFulfillmentNotificationPublisher implements FulfillmentNotificationPublisher {

    private final FulfillmentEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishStaleUpdateReminder(StaleUpdateReminderEvent event) {
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER)
                .projectId(event.projectId())
                .build());
    }

    @Override
    public void publishScheduleChanged(ScheduleChangedEvent event) {
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_SCHEDULE_CHANGED)
                .projectId(event.projectId())
                .stage(event.stage().name())
                .reasonType(event.reasonType().name())
                .newPlannedDate(event.newPlannedDate())
                .build());
    }

    @Override
    public void publishReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event) {
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_RECEIPT_AUTO_CONFIRMED)
                .fundingId(event.fundingId())
                .build());
    }
}
