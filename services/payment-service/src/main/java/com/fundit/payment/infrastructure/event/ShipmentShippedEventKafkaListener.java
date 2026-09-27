package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.refund.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * fulfillment-service의 발송 시작 이벤트를 구독해 교환 재발송 완료를 판정하는 얇은 어댑터
 * (비즈니스 로직은 {@link ExchangeService}). payload 레코드는 서비스마다 자기 것을 선언한다
 * (event-convention.md — 공유하면 생산자·소비자가 같이 배포돼야 하는 잠금이 생긴다).
 */
@Component
@RequiredArgsConstructor
public class ShipmentShippedEventKafkaListener {

    private final ExchangeService exchangeService;

    @KafkaListener(topics = KafkaTopics.SHIPMENT_SHIPPED, groupId = "payment-service")
    public void onShipmentShipped(ShipmentShippedEvent event) {
        exchangeService.onReshipmentShipped(event.fundingId(), event.reshipmentRefundRequestId());
    }

    /**
     * fulfillment-service {@code FulfillmentDomainEventPublisher.ShipmentShippedEvent}와 같은 JSON 계약.
     * {@code reshipmentRefundRequestId}는 교환 재발송분의 발송일 때만 채워진다(최초 발송이면 null).
     */
    public record ShipmentShippedEvent(UUID fundingId, UUID projectId, Long reshipmentRefundRequestId) {
    }
}
