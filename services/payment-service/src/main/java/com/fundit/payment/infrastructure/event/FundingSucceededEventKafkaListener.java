package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.settlement.FundingSucceededListener;
import com.fundit.payment.application.settlement.FundingSucceededListener.FundingSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PAYMENT-013 — order-service가 발행하는 펀딩 성립 이벤트를 구독해
 * {@link FundingSucceededListener}(={@code SettlementScheduleService})로 위임하는 얇은 어댑터.
 * order-service가 sellerId/achievedAt까지 채워 보낸다(event-convention.md 6번 "적용 예" —
 * FundingEventTransport.sendSucceeded 참고). 비즈니스 로직은 여기 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FundingSucceededEventKafkaListener {

    private final FundingSucceededListener listener;

    @KafkaListener(topics = KafkaTopics.FUNDING_SUCCEEDED, groupId = "payment-service")
    public void onFundingSucceeded(FundingSucceededEvent event) {
        listener.onFundingSucceeded(event);
    }
}
