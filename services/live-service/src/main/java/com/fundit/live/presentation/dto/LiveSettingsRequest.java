package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 부분 업데이트 — null인 필드는 건드리지 않는다. 임시저장을 이어서 작성하는 화면이라
 * 매번 전체를 보내지 않는다.
 *
 * <p>연결 프로젝트는 여기 없다. 요구사항정의서 6.2.4.1이 "변경 불가"로 정했고,
 * 바꾸려면 LIVE를 새로 만든다.
 *
 * <p>예약 해제는 {@code clearSchedule=true}로 보낸다 — {@code scheduledStartAt=null}은 이미
 * "변경 없음"이라 해제를 null로 표현할 수 없다.
 */
public record LiveSettingsRequest(
        Category category,
        @Size(max = 200) String introText,
        String thumbnailUrl,
        Instant scheduledStartAt,
        Boolean clearSchedule
) {
    public record Category(String major, String minor) {
    }

    public String majorOrNull() {
        return category == null ? null : category.major();
    }

    public String minorOrNull() {
        return category == null ? null : category.minor();
    }

    public boolean clearScheduleOrFalse() {
        return Boolean.TRUE.equals(clearSchedule);
    }

    @AssertTrue(message = "예약 해제와 예약 시각을 함께 보낼 수 없습니다.")
    public boolean isScheduleRequestConsistent() {
        return !clearScheduleOrFalse() || scheduledStartAt == null;
    }
}
