package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.payment.PaymentConfirmService.PaymentConfirmResult;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-002 응답. Funding.status는 포함하지 않는다 — order-service가 PaymentCompleted
 * 이벤트를 비동기로 구독해 반영하므로, 프론트엔드는 이 응답의 status 자체를 성공 판단 기준으로
 * 삼아야 한다(PaymentApiSpec.md 1-2 참고).
 */
public record PaymentConfirmResponse(UUID paymentId, Long fundingId, String status, String paymentMethod,
                                      String easyPayProvider, Instant paidAt) {

    public static PaymentConfirmResponse from(PaymentConfirmResult result) {
        return new PaymentConfirmResponse(result.paymentId(), result.fundingId(), result.status(),
                result.paymentMethod() == null ? null : result.paymentMethod().name(), result.easyPayProvider(),
                result.paidAt());
    }
}
