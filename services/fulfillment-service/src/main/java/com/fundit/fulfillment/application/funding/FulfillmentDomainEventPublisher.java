package com.fundit.fulfillment.application.funding;

import java.util.UUID;

/**
 * fulfillment-service가 발행하는 도메인 이벤트(알림 명령이 아니라 다른 서비스가 소비하는
 * 사실)의 아웃바운드 포트. 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다
 * ({@code OutboxFulfillmentDomainEventPublisher}) — order-service {@code FundingEventPublisher}와
 * 동일 패턴. {@code FulfillmentNotificationPublisher}(알림)와는 목적이 달라 분리돼 있다.
 */
public interface FulfillmentDomainEventPublisher {

    /** FULFILLMENT-007 — 배송완료 시점, payment-service PAYMENT-014(최종정산) 트리거. */
    void publishShippingCompleted(ShippingCompletedEvent event);

    /**
     * FULFILLMENT-006 — 발송정보 등록(PREPARING→SHIPPED) 시점, order-service 판매자 발송목록의
     * 발송상태 필터·건수 캐시 갱신 트리거. {@link #publishShippingCompleted}(SHIPPED→DELIVERED)와는
     * 다른 전이 시점이다.
     */
    void publishShipmentShipped(ShipmentShippedEvent event);

    record ShippingCompletedEvent(UUID fundingId, UUID projectId) {
    }

    record ShipmentShippedEvent(UUID fundingId, UUID projectId) {
    }
}
