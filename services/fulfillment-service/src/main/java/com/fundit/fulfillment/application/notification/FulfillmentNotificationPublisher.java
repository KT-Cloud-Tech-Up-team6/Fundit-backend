package com.fundit.fulfillment.application.notification;

import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.time.Instant;

/**
 * fulfillment-service가 발행하는 알림 이벤트의 아웃바운드 포트(FULFILLMENT-004/005/010).
 * notification-service가 아직 없어(root CLAUDE.md "예정" 서비스) 실제 구독 주체가 없다 —
 * 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다({@code OutboxFulfillmentNotificationPublisher}).
 * 실제 채널 발행은 워커가 재시도하고, notification-service/브로커가 확정되면
 * {@code FulfillmentNotificationTransport} 구현체만 교체한다(order-service
 * {@code FundingEventPublisher}와 동일 패턴).
 */
public interface FulfillmentNotificationPublisher {

    /** FULFILLMENT-004 — 1주 미갱신 트래커에 대한 판매자 업데이트 요청 알림. */
    void publishStaleUpdateReminder(StaleUpdateReminderEvent event);

    /** FULFILLMENT-005 — 일정 변경 시 구매자 안내 알림. */
    void publishScheduleChanged(ScheduleChangedEvent event);

    /** FULFILLMENT-010 — 미확인 자동확정 시 구매자 안내 알림. */
    void publishReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event);

    record StaleUpdateReminderEvent(Long projectId) {
    }

    record ScheduleChangedEvent(Long projectId, FulfillmentStage stage, ScheduleChangeReasonType reasonType,
                                 Instant newPlannedDate) {
    }

    record ReceiptAutoConfirmedEvent(Long fundingId) {
    }
}
