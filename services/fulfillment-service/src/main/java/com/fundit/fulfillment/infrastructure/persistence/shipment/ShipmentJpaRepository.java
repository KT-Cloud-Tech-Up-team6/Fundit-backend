package com.fundit.fulfillment.infrastructure.persistence.shipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentJpaEntity, Long> {

    Optional<ShipmentJpaEntity> findByFundingOrderId(UUID fundingOrderId);

    List<ShipmentJpaEntity> findByFundingOrderIdIn(List<UUID> fundingOrderIds);

    List<ShipmentJpaEntity> findByStatusAndShippedAtBefore(String status, Instant threshold);

    List<ShipmentJpaEntity> findByStatusAndDeliveredAtBefore(String status, Instant threshold);
}
