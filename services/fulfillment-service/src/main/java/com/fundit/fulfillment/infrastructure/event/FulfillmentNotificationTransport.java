package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;

import java.util.UUID;

/**
 * 아웃박스에 적재된 알림 이벤트를 실제 채널({@code notification.raised.v1})로 보내는 전송 포트.
 * {@code memberId}는 도메인 이벤트 자체에는 없고 아웃박스 행이 담고 있는 수신자 정보라 별도
 * 파라미터로 받는다(outboxId와 같은 성격 — 발행 메타데이터).
 */
public interface FulfillmentNotificationTransport {

    /**
     * outboxId는 소비 측 멱등의 근거가 되는 eventId("fulfillment:{outboxId}")의 재료다(event-convention.md 5번).
     * projectPublicId는 relatedUrl 조립용(외부 노출 식별자 — 내부 PK를 URL에 쓰지 않는다).
     */
    void sendStaleUpdateReminder(StaleUpdateReminderEvent event, UUID memberId, UUID projectPublicId, Long outboxId);

    /** projectPublicId는 relatedUrl 조립용(외부 노출 식별자 — 내부 PK를 URL에 쓰지 않는다). */
    void sendScheduleChanged(ScheduleChangedEvent event, UUID memberId, UUID projectPublicId, Long outboxId);

    /** fundingPublicId는 relatedUrl 조립용(외부 노출 식별자 — 내부 PK를 URL에 쓰지 않는다). */
    void sendReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event, UUID memberId, UUID fundingPublicId, Long outboxId);
}
