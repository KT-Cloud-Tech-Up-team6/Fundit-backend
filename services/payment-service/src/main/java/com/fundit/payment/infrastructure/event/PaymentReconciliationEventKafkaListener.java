package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.refund.PaymentReconciliationListener;
import com.fundit.payment.application.refund.PaymentReconciliationListener.PaymentReconciliationRequiredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PAYMENT-017 — order-service가 발행할 결제 대사 필요 이벤트를 구독해
 * {@link PaymentReconciliationListener}(={@code PaymentReconciliationService})로 위임하는
 * 얇은 어댑터. order-service가 아직 이 토픽으로 발행하지 않아(Tier C, `.claude/plans/` 참고)
 * 지금은 이 어댑터를 붙여도 실제 트래픽이 없다 — 발행 측이 준비되면 바로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationEventKafkaListener {

    private final PaymentReconciliationListener listener;

    @KafkaListener(topics = KafkaTopics.PAYMENT_RECONCILIATION_REQUIRED, groupId = "payment-service")
    public void onPaymentReconciliationRequired(PaymentReconciliationRequiredEvent event) {
        listener.onPaymentReconciliationRequired(event);
    }
}
