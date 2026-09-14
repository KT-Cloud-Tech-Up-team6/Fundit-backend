package com.fundit.fulfillment.infrastructure.persistence.shipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentJpaEntity, Long> {

    Optional<ShipmentJpaEntity> findByFundingId(Long fundingId);

    List<ShipmentJpaEntity> findByStatusAndShippedAtBefore(String status, Instant threshold);

    List<ShipmentJpaEntity> findByStatusAndDeliveredAtBefore(String status, Instant threshold);
}
