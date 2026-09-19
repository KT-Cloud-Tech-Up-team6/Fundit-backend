package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.payment.PaymentConfirmService.PaymentConfirmResult;

import java.time.Instant;
import java.util.UUID;

/** PAYMENT-002 v2 응답 — fundingId가 order-service publicId(UUID)로 채워진다. */
public record PaymentConfirmResponseV2(UUID paymentId, UUID fundingId, String status, String paymentMethod,
                                        String easyPayProvider, Instant paidAt) {

    public static PaymentConfirmResponseV2 from(PaymentConfirmResult result) {
        return new PaymentConfirmResponseV2(result.paymentId(), result.fundingId(), result.status(),
                result.paymentMethod() == null ? null : result.paymentMethod().name(), result.easyPayProvider(),
                result.paidAt());
    }
}
