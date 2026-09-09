package com.fundit.order.presentation.dto;

import com.fundit.order.application.supporter.SupporterActivityService;

import java.time.Duration;
import java.time.Instant;

public record SupporterActivityResponse(String displayName, Long amount, String relativeTime) {

    public static SupporterActivityResponse from(SupporterActivityService.SupporterActivity activity) {
        return new SupporterActivityResponse(activity.displayName(), activity.amount(),
                toRelativeTime(activity.activityAt()));
    }

    private static String toRelativeTime(Instant activityAt) {
        Duration elapsed = Duration.between(activityAt, Instant.now());
        if (elapsed.toMinutes() < 1) {
            return "방금 전";
        }
        if (elapsed.toMinutes() < 60) {
            return elapsed.toMinutes() + "분 전";
        }
        if (elapsed.toHours() < 24) {
            return elapsed.toHours() + "시간 전";
        }
        return elapsed.toDays() + "일 전";
    }
}
