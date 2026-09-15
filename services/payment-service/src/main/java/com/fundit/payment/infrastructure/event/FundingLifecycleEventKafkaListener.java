package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.refund.FundingLifecycleEventListener;
import com.fundit.payment.application.refund.FundingLifecycleEventListener.FundingCancelledByMemberEvent;
import com.fundit.payment.application.refund.FundingLifecycleEventListener.FundingGoalFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PAYMENT-004/005 — order-service가 발행하는 펀딩 취소/미달 이벤트를 구독해
 * {@link FundingLifecycleEventListener}(={@code FundingLifecycleEventSyncService})로
 * 위임하는 얇은 어댑터. 비즈니스 로직은 여기 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FundingLifecycleEventKafkaListener {

    private final FundingLifecycleEventListener listener;

    @KafkaListener(topics = KafkaTopics.FUNDING_CANCELLED_BY_MEMBER, groupId = "payment-service")
    public void onFundingCancelledByMember(FundingCancelledByMemberEvent event) {
        listener.onFundingCancelledByMember(event);
    }

    @KafkaListener(topics = KafkaTopics.FUNDING_GOAL_FAILED, groupId = "payment-service")
    public void onFundingGoalFailed(FundingGoalFailedEvent event) {
        listener.onFundingGoalFailed(event);
    }
}
