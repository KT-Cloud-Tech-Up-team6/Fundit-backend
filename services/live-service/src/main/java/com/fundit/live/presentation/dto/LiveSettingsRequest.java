package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 부분 업데이트 — null인 필드는 건드리지 않는다. 임시저장을 이어서 작성하는 화면이라
 * 매번 전체를 보내지 않는다.
 *
 * <p>연결 프로젝트는 여기 없다. 요구사항정의서 6.2.4.1이 "변경 불가"로 정했고,
 * 바꾸려면 LIVE를 새로 만든다.
 */
public record LiveSettingsRequest(
        Category category,
        @Size(max = 200) String introText,
        String thumbnailUrl,
        Instant scheduledStartAt
) {
    public record Category(String major, String minor) {
    }

    public String majorOrNull() {
        return category == null ? null : category.major();
    }

    public String minorOrNull() {
        return category == null ? null : category.minor();
    }
}
