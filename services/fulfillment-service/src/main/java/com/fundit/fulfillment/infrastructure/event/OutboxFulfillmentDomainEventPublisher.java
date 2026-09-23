package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송완료 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link FulfillmentDomainEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxFulfillmentDomainEventPublisher implements FulfillmentDomainEventPublisher {

    private final FulfillmentDomainEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishShippingCompleted(ShippingCompletedEvent event) {
        outboxRepository.save(FulfillmentDomainEventOutboxJpaEntity.builder()
                .eventType(FulfillmentDomainEventOutboxJpaEntity.TYPE_SHIPPING_COMPLETED)
                .fundingOrderId(event.fundingId())
                .projectPublicId(event.projectId())
                .build());
    }

    @Override
    public void publishShipmentShipped(ShipmentShippedEvent event) {
        outboxRepository.save(FulfillmentDomainEventOutboxJpaEntity.builder()
                .eventType(FulfillmentDomainEventOutboxJpaEntity.TYPE_SHIPMENT_SHIPPED)
                .fundingOrderId(event.fundingId())
                .projectPublicId(event.projectId())
                .build());
    }
}
