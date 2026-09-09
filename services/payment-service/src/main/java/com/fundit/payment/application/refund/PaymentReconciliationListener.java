package com.fundit.payment.application.refund;

import java.util.UUID;

/**
 * PAYMENT-017 — order-service가 발행하는 {@code PaymentReconciliationRequired} 구독 포트.
 * order-service가 아직 이 이벤트를 발행하지 않는다(payment-service CLAUDE.md "정책값 확인
 * 필요" — 연동 이슈에서 함께 처리). 비즈니스 로직은 완성해두고 실제 트리거 배선만 비워둔다.
 */
public interface PaymentReconciliationListener {

    void onPaymentReconciliationRequired(PaymentReconciliationRequiredEvent event);

    record PaymentReconciliationRequiredEvent(Long fundingId, UUID paymentId) {
    }
}
