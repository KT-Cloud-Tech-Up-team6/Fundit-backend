package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;

import java.util.UUID;

/**
 * 하이라이트 성과 통계(요구사항정의서 6.6.4).
 *
 * <p>{@code impressionCount}는 <b>노출 수</b>다 — 공개 목록이 열릴 때 그 세션의 공개 항목이
 * 전부 +1 된다. "이 클립이 몇 번 재생됐나"가 아니라 "목록에 몇 번 실렸나"이므로 이름을 그렇게 뒀다.
 * 클립 단위 재생 수를 원하면 재생 이벤트를 받을 경로가 먼저 필요하다.
 *
 * <p>{@code fundingConversionCount}가 없다 — 펀딩 전환 기여는 order 집계와 대조해야 하는데
 * 그 집계 주체가 아직 정해지지 않았다. 채울 수 없는 필드를 응답에 두면 프론트가
 * 0을 실제 값으로 오해한다. 집계 주체가 확정되면 그때 추가한다.
 */
public record HighlightStatsResponse(UUID highlightId, int impressionCount, int clickCount) {

    public static HighlightStatsResponse from(LiveHighlightJpaEntity e) {
        return new HighlightStatsResponse(e.getPublicId(), e.getViewCount(), e.getClickCount());
    }
}
