package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;

import java.time.Instant;

/**
 * API #5(발송정보 등록)/#6(배송현황 조회)/#7(수령확인) 공용 응답. {@code canConfirmReceipt}는
 * API #6 조회 화면의 버튼 노출 여부에만 실질적으로 쓰이지만, 상태만으로 계산 가능한 값이라
 * 세 엔드포인트 모두 동일하게 채운다(엔드포인트별 응답 DTO를 분리하지 않기 위한 단순화).
 */
public record ShipmentResponse(Long fundingId, ShipmentStatus status, String carrier, String trackingNumber,
                                Instant shippedAt, Instant deliveredAt, Instant receiptConfirmedAt,
                                boolean canConfirmReceipt) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(shipment.getFundingId(), shipment.getStatus(), shipment.getCarrier(),
                shipment.getTrackingNumber(), shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getReceiptConfirmedAt(), shipment.canConfirmReceipt());
    }
}
