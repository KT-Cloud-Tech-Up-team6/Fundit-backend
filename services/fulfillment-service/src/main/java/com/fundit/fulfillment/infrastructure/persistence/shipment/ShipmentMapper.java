package com.fundit.fulfillment.infrastructure.persistence.shipment;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.springframework.stereotype.Component;

@Component
class ShipmentMapper {

    Shipment toDomain(ShipmentJpaEntity entity) {
        return Shipment.builder()
                .id(entity.getId())
                .fundingId(entity.getFundingId())
                .projectId(entity.getProjectId())
                .status(ShipmentStatus.valueOf(entity.getStatus()))
                .carrier(entity.getCarrier())
                .trackingNumber(entity.getTrackingNumber())
                .shippedAt(entity.getShippedAt())
                .deliveredAt(entity.getDeliveredAt())
                .receiptConfirmedAt(entity.getReceiptConfirmedAt())
                .receiptAutoConfirmed(entity.isReceiptAutoConfirmed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    ShipmentJpaEntity toEntity(Shipment domain) {
        return ShipmentJpaEntity.builder()
                .id(domain.getId())
                .fundingId(domain.getFundingId())
                .projectId(domain.getProjectId())
                .status(domain.getStatus().name())
                .carrier(domain.getCarrier())
                .trackingNumber(domain.getTrackingNumber())
                .shippedAt(domain.getShippedAt())
                .deliveredAt(domain.getDeliveredAt())
                .receiptConfirmedAt(domain.getReceiptConfirmedAt())
                .receiptAutoConfirmed(domain.isReceiptAutoConfirmed())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
