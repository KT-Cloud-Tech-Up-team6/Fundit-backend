package com.fundit.fulfillment.infrastructure.persistence.shipment;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ShipmentPersistenceAdapter implements ShipmentRepository {

    private final ShipmentJpaRepository jpaRepository;
    private final ShipmentMapper mapper;

    @Override
    public Optional<Shipment> findByFundingId(UUID fundingId) {
        return jpaRepository.findByFundingOrderId(fundingId).map(mapper::toDomain);
    }

    @Override
    public Optional<Shipment> findByFundingIdForUpdate(UUID fundingId) {
        return jpaRepository.findForUpdateByFundingOrderId(fundingId).map(mapper::toDomain);
    }

    @Override
    public List<Shipment> findByFundingIdIn(List<UUID> fundingIds) {
        return jpaRepository.findByFundingOrderIdIn(fundingIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Shipment save(Shipment shipment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(shipment)));
    }

    @Override
    public List<Shipment> findByStatusAndShippedAtBefore(ShipmentStatus status, Instant threshold) {
        return jpaRepository.findByStatusAndShippedAtBefore(status.name(), threshold)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Shipment> findByStatusAndDeliveredAtBefore(ShipmentStatus status, Instant threshold) {
        return jpaRepository.findByStatusAndDeliveredAtBefore(status.name(), threshold)
                .stream().map(mapper::toDomain).toList();
    }
}
