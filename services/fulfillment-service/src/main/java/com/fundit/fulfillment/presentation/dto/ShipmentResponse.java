package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;

import java.time.Instant;

/**
 * v1 응답 — cross-service ID 통일(#69) 이후 fundingId(Long)는 더 이상 채울 수 없어
 * 항상 {@code null}이다(알려진 한계). 실제 식별자가 필요하면 {@link ShipmentResponseV2}를 쓸 것.
 */
public record ShipmentResponse(Long fundingId, ShipmentStatus status, String carrier, String trackingNumber,
                                Instant shippedAt, Instant deliveredAt, Instant receiptConfirmedAt,
                                boolean canConfirmReceipt) {

    /** 발송 전 임시저장 송장 마스킹 규칙은 v2와 같다 — {@link ShipmentResponseV2#from} 참고. */
    public static ShipmentResponse from(Shipment shipment) {
        boolean shipped = shipment.getStatus().ordinal() >= ShipmentStatus.SHIPPED.ordinal();
        return new ShipmentResponse(null, shipment.getStatus(),
                shipped ? shipment.getCarrier() : null, shipped ? shipment.getTrackingNumber() : null,
                shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getReceiptConfirmedAt(), shipment.canConfirmReceipt());
    }
}
