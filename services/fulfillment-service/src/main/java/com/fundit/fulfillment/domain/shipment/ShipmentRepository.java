package com.fundit.fulfillment.domain.shipment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    Optional<Shipment> findByFundingId(UUID fundingId);

    /** payment-service/order-service 배치 조회용(FULFILLMENT-008 배치 변형). */
    List<Shipment> findByFundingIdIn(List<UUID> fundingIds);

    Shipment save(Shipment shipment);

    /** FULFILLMENT-007 배치 대상 조회 — 발송 후 threshold 이전에 발송된 SHIPPED 건. */
    List<Shipment> findByStatusAndShippedAtBefore(ShipmentStatus status, Instant threshold);

    /** FULFILLMENT-010 배치 대상 조회 — 배송완료 후 threshold 이전에 완료된 DELIVERED 건. */
    List<Shipment> findByStatusAndDeliveredAtBefore(ShipmentStatus status, Instant threshold);
}
