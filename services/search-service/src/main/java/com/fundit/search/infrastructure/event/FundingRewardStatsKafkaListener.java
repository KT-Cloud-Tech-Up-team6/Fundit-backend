package com.fundit.search.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.search.application.projectdocument.FundingRewardStatsEventListener;
import com.fundit.search.application.projectdocument.FundingRewardStatsEventListener.RewardStatsUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SEARCH-013 — {@code project.funding-reward-stats-updated.v1} 구독 어댑터.
 * 컨슈머 그룹은 {@code application.yml}의 {@code search-service}를 그대로 쓴다 —
 * project-service(그룹 {@code project-service})와 달라야 양쪽이 모두 받는다.
 */
@Component
@RequiredArgsConstructor
public class FundingRewardStatsKafkaListener {

    private final FundingRewardStatsEventListener fundingRewardStatsEventListener;

    @KafkaListener(topics = KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED)
    public void onRewardStatsUpdated(RewardStatsUpdatedEvent event) {
        if (event.projectId() == null) {
            return;
        }
        fundingRewardStatsEventListener.onRewardStatsUpdated(event);
    }
}
