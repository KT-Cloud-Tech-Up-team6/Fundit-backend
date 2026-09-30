package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.FulfillmentStatusView;

import java.time.Instant;

/**
 * FULFILLMENT-008 — API #8 응답(payment-service 연동 내부 전용).
 *
 * <p>{@code hasProgressRecord}는 order-service의 진행 단계 배지("제작 중")용으로 추가된 필드다
 * (payment-service {@code ShippingStatusClient}는 모르는 필드를 무시하므로 영향이 없다).
 */
public record FulfillmentStatusInternalResponse(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                                                 Instant receiptConfirmedAt, boolean hasProgressRecord) {

    public static FulfillmentStatusInternalResponse from(FulfillmentStatusView view) {
        return new FulfillmentStatusInternalResponse(view.isAlreadyShipped(), view.isDelayed(), view.deliveredAt(),
                view.receiptConfirmedAt(), view.hasProgressRecord());
    }
}
