package com.fundit.fulfillment.infrastructure.persistence.shipment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentJpaEntity, Long> {

    Optional<ShipmentJpaEntity> findByFundingOrderId(UUID fundingOrderId);

    /** 상태를 바꾸는 경로 전용 — 같은 행을 읽고 고쳐 저장하는 트랜잭션끼리 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShipmentJpaEntity> findForUpdateByFundingOrderId(UUID fundingOrderId);

    List<ShipmentJpaEntity> findByFundingOrderIdIn(List<UUID> fundingOrderIds);

    List<ShipmentJpaEntity> findByStatusAndShippedAtBefore(String status, Instant threshold);

    List<ShipmentJpaEntity> findByStatusAndDeliveredAtBefore(String status, Instant threshold);
}
