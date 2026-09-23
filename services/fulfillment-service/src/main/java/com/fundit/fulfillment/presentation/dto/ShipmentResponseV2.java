package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

/** v2 응답 — fundingId가 order-service orderId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record ShipmentResponseV2(UUID fundingId, ShipmentStatus status, String carrier, String trackingNumber,
                                  Instant shippedAt, Instant deliveredAt, Instant receiptConfirmedAt,
                                  boolean canConfirmReceipt) {

    /**
     * 구매자에게 나가는 기본 변환 — 발송(SHIPPED) 전이면 송장을 가린다. 판매자가 발송 전에
     * 임시저장해 둔 택배사·운송장이 "아직 발송 안 된 건"의 배송현황에 그대로 보이면 안 된다.
     * 판매자 경로는 {@link #forSeller}를 쓴다.
     */
    public static ShipmentResponseV2 from(Shipment shipment) {
        boolean shipped = shipment.getStatus().ordinal() >= ShipmentStatus.SHIPPED.ordinal();
        return build(shipment, shipped ? shipment.getCarrier() : null,
                shipped ? shipment.getTrackingNumber() : null);
    }

    /** 판매자 경로(발송 처리·임시저장·발송 목록) — 자기가 입력한 값이라 가리지 않는다. */
    public static ShipmentResponseV2 forSeller(Shipment shipment) {
        return build(shipment, shipment.getCarrier(), shipment.getTrackingNumber());
    }

    private static ShipmentResponseV2 build(Shipment shipment, String carrier, String trackingNumber) {
        return new ShipmentResponseV2(shipment.getFundingId(), shipment.getStatus(), carrier, trackingNumber,
                shipment.getShippedAt(), shipment.getDeliveredAt(), shipment.getReceiptConfirmedAt(),
                shipment.canConfirmReceipt());
    }
}
