package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.FulfillmentStatusView;

import java.time.Instant;

/** FULFILLMENT-008 — API #8 응답(payment-service 연동 내부 전용). */
public record FulfillmentStatusInternalResponse(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                                                 Instant receiptConfirmedAt) {

    public static FulfillmentStatusInternalResponse from(FulfillmentStatusView view) {
        return new FulfillmentStatusInternalResponse(view.isAlreadyShipped(), view.isDelayed(), view.deliveredAt(),
                view.receiptConfirmedAt());
    }
}
