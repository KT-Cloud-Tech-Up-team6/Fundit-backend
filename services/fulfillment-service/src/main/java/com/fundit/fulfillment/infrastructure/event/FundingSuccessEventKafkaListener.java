package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.fulfillment.application.funding.FundingSuccessEventListener;
import com.fundit.fulfillment.application.funding.FundingSuccessEventListener.FundingSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FULFILLMENT-001 — order-service가 발행하는 펀딩 성립 이벤트를 구독해
 * {@link FundingSuccessEventListener}(={@code FulfillmentTrackerInitializationService})로
 * 위임하는 얇은 어댑터. 비즈니스 로직은 여기 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FundingSuccessEventKafkaListener {

    private final FundingSuccessEventListener listener;

    @KafkaListener(topics = KafkaTopics.FUNDING_SUCCEEDED, groupId = "fulfillment-service")
    public void onFundingSucceeded(FundingSucceededEvent event) {
        listener.onFundingSucceeded(event);
    }
}
