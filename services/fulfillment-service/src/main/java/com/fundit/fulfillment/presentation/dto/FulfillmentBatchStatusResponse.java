package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.FulfillmentBatchStatusView;

import java.time.Instant;
import java.util.UUID;

/** order-service 주문 목록(V03/V06)이 배치로 호출하는 배송 상태 조회 응답. */
public record FulfillmentBatchStatusResponse(UUID fundingId, boolean isAlreadyShipped, Instant deliveredAt) {

    public static FulfillmentBatchStatusResponse from(FulfillmentBatchStatusView view) {
        return new FulfillmentBatchStatusResponse(view.fundingId(), view.isAlreadyShipped(), view.deliveredAt());
    }
}
