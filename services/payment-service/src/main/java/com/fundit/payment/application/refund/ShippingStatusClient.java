package com.fundit.payment.application.refund;

import java.time.Instant;
import java.util.UUID;

/**
 * FS-096(발송지연 판정) 결과 확인용 아웃바운드 포트(PAYMENT-008). fulfillment-service의
 * 내부 API(FULFILLMENT-008, {@code GET /internal/fundings/{fundingId}/fulfillment-status})
 * 응답 4개 필드와 1:1 대응한다.
 */
public interface ShippingStatusClient {

    ShippingStatus fetch(Long fundingId);

    /** v2 — order-service orderId(UUID). fulfillment-service PathVariable도 UUID로 전환된다. */
    ShippingStatus fetch(UUID orderId);

    record ShippingStatus(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt, Instant receiptConfirmedAt) {
    }
}
