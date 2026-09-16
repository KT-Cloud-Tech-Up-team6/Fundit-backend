package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.settlement.ShippingCompletionListener;
import com.fundit.payment.application.settlement.ShippingCompletionListener.ShippingCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PAYMENT-014 — fulfillment-service가 발행하는 배송완료 이벤트를 구독해
 * {@link ShippingCompletionListener}(={@code SettlementScheduleService})로 위임하는 얇은 어댑터.
 * 비즈니스 로직은 여기 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ShippingCompletedEventKafkaListener {

    private final ShippingCompletionListener listener;

    @KafkaListener(topics = KafkaTopics.SHIPPING_COMPLETED, groupId = "payment-service")
    public void onShippingCompleted(ShippingCompletedEvent event) {
        listener.onShippingCompleted(event);
    }
}
