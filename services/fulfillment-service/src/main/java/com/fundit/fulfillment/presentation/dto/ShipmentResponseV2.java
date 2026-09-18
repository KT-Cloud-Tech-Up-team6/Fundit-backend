package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

/** v2 응답 — fundingId가 order-service orderId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record ShipmentResponseV2(UUID fundingId, ShipmentStatus status, String carrier, String trackingNumber,
                                  Instant shippedAt, Instant deliveredAt, Instant receiptConfirmedAt,
                                  boolean canConfirmReceipt) {

    public static ShipmentResponseV2 from(Shipment shipment) {
        return new ShipmentResponseV2(shipment.getFundingId(), shipment.getStatus(), shipment.getCarrier(),
                shipment.getTrackingNumber(), shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getReceiptConfirmedAt(), shipment.canConfirmReceipt());
    }
}
