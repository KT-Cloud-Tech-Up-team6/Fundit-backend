package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.payment.PaymentConfirmService.PaymentConfirmResult;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-002 v1 응답. cross-service ID 통일(#69) 이후 결제 도메인은 UUID만 저장해
 * {@code fundingId}(Long)는 항상 {@code null}이다. 실제 식별자가 필요하면
 * {@link PaymentConfirmResponseV2}를 쓸 것.
 */
public record PaymentConfirmResponse(UUID paymentId, Long fundingId, String status, String paymentMethod,
                                      String easyPayProvider, Instant paidAt) {

    public static PaymentConfirmResponse from(PaymentConfirmResult result) {
        return new PaymentConfirmResponse(result.paymentId(), null, result.status(),
                result.paymentMethod() == null ? null : result.paymentMethod().name(), result.easyPayProvider(),
                result.paidAt());
    }
}
