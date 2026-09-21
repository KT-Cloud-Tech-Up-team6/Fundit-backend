package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.payment.PaymentEventListener;
import com.fundit.order.application.payment.PaymentEventListener.PaymentCompletedEvent;
import com.fundit.order.application.payment.PaymentEventListener.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ORDER-015 — payment-service가 발행하는 결제완료/환불 이벤트를 구독해
 * {@link PaymentEventListener}(={@code PaymentEventSyncService})로 위임하는 얇은 어댑터.
 * 비즈니스 로직은 여기 두지 않는다({@link RewardEventKafkaListener}와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class PaymentEventKafkaListener {

    private final PaymentEventListener listener;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "order-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        listener.onPaymentCompleted(event);
    }

    @KafkaListener(topics = KafkaTopics.REFUND_COMPLETED, groupId = "order-service")
    public void onRefundCompleted(RefundCompletedEvent event) {
        listener.onRefundCompleted(event);
    }
}
