package com.fundit.project.presentation.dto;

import java.time.Instant;

public record FundingStatusSummaryResponse(long currentAmount, int achievementRate, int participantCount,
                                           Long remainingDays, Instant fundingDeadline) {
}
