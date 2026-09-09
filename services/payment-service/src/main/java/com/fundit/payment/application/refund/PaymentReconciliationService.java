package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PAYMENT-017 — 결제-재고만료 충돌 자동 환불. order-service가 {@code PaymentCompleted} 처리 중
 * 이미 {@code PAYMENT_EXPIRED}로 전이된 것을 감지했을 때 발행하는 {@code PaymentReconciliationRequired}를
 * 구독해 PAYMENT-004와 동일하게 전액취소한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService implements PaymentReconciliationListener {

    private final RefundExecutionService refundExecutionService;

    @Override
    public void onPaymentReconciliationRequired(PaymentReconciliationRequiredEvent event) {
        refundExecutionService.executeFullRefund(event.fundingId(), RefundTriggerType.SYSTEM_RECONCILIATION,
                "재고 확보 실패로 인한 시스템 자동 환불");
    }
}
