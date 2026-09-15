package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShippingCompletedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 아웃박스에 적재된 도메인 이벤트를 실제 채널로 보내는 전송 포트.
 * 브로커(Kafka)가 확정되면 이 인터페이스의 구현체만 교체한다(order-service
 * {@code FundingEventTransport}와 동일 패턴).
 */
public interface FulfillmentDomainEventTransport {

    /**
     * sellerId/completedAt은 payment-service {@code ShippingCompletionListener}가 기대하는 필드다.
     * fulfillment-service는 sellerId를 로컬에 갖고 있지 않으므로(project-service 소유) 배선
     * 계층(워커)에서 project-service를 조회해 채운다 — order-service
     * {@code FundingEventTransport.sendSucceeded}와 동일한 이유·패턴.
     * outboxId는 소비 측 멱등의 근거가 되는 eventId("fulfillment:{outboxId}")의 재료다.
     */
    void sendShippingCompleted(ShippingCompletedEvent event, UUID sellerId, Instant completedAt, Long outboxId);
}
