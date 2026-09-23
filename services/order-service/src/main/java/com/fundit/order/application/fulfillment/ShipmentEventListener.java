package com.fundit.order.application.fulfillment;

import java.time.Instant;
import java.util.UUID;

/**
 * #129 — fulfillment-service가 아웃박스로 발행하는 발송 시작 이벤트를 처리하는 인바운드 포트.
 * 이벤트 필드는 fulfillment-service {@code FulfillmentDomainEventPublisher.ShipmentShippedEvent}와
 * 동일한 계약이다(event-convention.md 4번 — payload는 각 서비스가 자기 record를 선언).
 */
public interface ShipmentEventListener {

    void onShipmentShipped(ShipmentShippedEvent event);

    record ShipmentShippedEvent(UUID fundingId, UUID projectId, Instant shippedAt) {
    }
}
