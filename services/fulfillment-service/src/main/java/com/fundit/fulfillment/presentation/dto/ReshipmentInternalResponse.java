package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.ExchangeReshipmentService.ReshipmentResult;

/**
 * 재발송 착수 결과. {@code status}는 재발송 직후라면 PREPARING이고(판매자 새 운송장 등록 대기),
 * {@code reshipmentCount}는 누적 재발송 횟수다 — 멱등 무시된 재요청에서는 증가하지 않는다.
 */
public record ReshipmentInternalResponse(String status, int reshipmentCount) {

    public static ReshipmentInternalResponse from(ReshipmentResult result) {
        return new ReshipmentInternalResponse(result.status(), result.reshipmentCount());
    }
}
