package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.fulfillment.ShipmentEventListener;
import com.fundit.order.application.fulfillment.ShipmentEventListener.ShipmentShippedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * #129 — fulfillment-service가 발행하는 발송 시작 이벤트를 구독해 {@link ShipmentEventListener}
 * (={@code ShipmentEventSyncService})로 위임하는 얇은 어댑터. 비즈니스 로직은 여기 두지 않는다
 * ({@link RewardEventKafkaListener}와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class ShipmentEventKafkaListener {

    private final ShipmentEventListener listener;

    @KafkaListener(topics = KafkaTopics.SHIPMENT_SHIPPED, groupId = "order-service")
    public void onShipmentShipped(ShipmentShippedEvent event) {
        listener.onShipmentShipped(event);
    }
}
