package com.fundit.project.presentation.dto;

public record FundingStatusSummaryResponse(long currentAmount, int achievementRate, int participantCount, Long remainingDays) {
}
