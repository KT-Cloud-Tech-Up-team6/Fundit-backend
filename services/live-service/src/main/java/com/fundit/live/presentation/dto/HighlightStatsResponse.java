package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;

import java.util.UUID;

/**
 * 하이라이트 성과 통계(요구사항정의서 6.6.4).
 *
 * <p>{@code fundingConversionCount}가 없다 — 펀딩 전환 기여는 order 집계와 대조해야 하는데
 * 그 집계 주체가 아직 정해지지 않았다. 채울 수 없는 필드를 응답에 두면 프론트가
 * 0을 실제 값으로 오해한다. 집계 주체가 확정되면 그때 추가한다.
 */
public record HighlightStatsResponse(UUID highlightId, int viewCount, int clickCount) {

    public static HighlightStatsResponse from(LiveHighlightJpaEntity e) {
        return new HighlightStatsResponse(e.getPublicId(), e.getViewCount(), e.getClickCount());
    }
}
