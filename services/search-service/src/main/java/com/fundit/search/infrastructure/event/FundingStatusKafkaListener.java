package com.fundit.search.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.search.application.projectdocument.FundingStatusEventListener;
import com.fundit.search.application.projectdocument.FundingStatusEventListener.FundingGoalFailedEvent;
import com.fundit.search.application.projectdocument.FundingStatusEventListener.FundingSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SEARCH-012 — {@code funding.succeeded.v1}/{@code funding.goal-failed.v1} 구독 어댑터.
 * 인바운드 포트로 위임만 하는 얇은 어댑터다(notification-service {@code NotificationKafkaListener}와 동일 형태).
 */
@Component
@RequiredArgsConstructor
public class FundingStatusKafkaListener {

    private final FundingStatusEventListener fundingStatusEventListener;

    @KafkaListener(topics = KafkaTopics.FUNDING_SUCCEEDED)
    public void onFundingSucceeded(FundingSucceededEvent event) {
        fundingStatusEventListener.onFundingSucceeded(event);
    }

    @KafkaListener(topics = KafkaTopics.FUNDING_GOAL_FAILED)
    public void onFundingGoalFailed(FundingGoalFailedEvent event) {
        fundingStatusEventListener.onFundingGoalFailed(event);
    }
}
