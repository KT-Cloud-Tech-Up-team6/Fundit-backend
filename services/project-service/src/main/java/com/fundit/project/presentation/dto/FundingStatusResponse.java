package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.List;

public record FundingStatusResponse(
        long currentAmount, int achievementRate, int participantCount, long openNotifyCount, int wishCount,
        List<RewardStatResponse> rewardStats, Long remainingDays, Instant lastSyncedAt) {
}
