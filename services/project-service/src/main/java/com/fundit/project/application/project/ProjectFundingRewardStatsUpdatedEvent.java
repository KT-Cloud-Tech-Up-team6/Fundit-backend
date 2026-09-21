package com.fundit.project.application.project;

import com.fundit.project.domain.fundingstatus.RewardStat;

import java.util.List;

/**
 * order-service가 발행하는 {@code project.funding-reward-stats-updated.v1} 계약.
 * JSON 필드명은 order {@code FundingRewardStatsPublisher.RewardStatsUpdatedEvent}와 같다.
 */
public record ProjectFundingRewardStatsUpdatedEvent(Long projectId, List<RewardStat> rewardStats) {
}
