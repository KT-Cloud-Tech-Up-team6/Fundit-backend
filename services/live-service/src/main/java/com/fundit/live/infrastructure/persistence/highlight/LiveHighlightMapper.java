package com.fundit.live.infrastructure.persistence.highlight;

import com.fundit.live.domain.highlight.LiveHighlight;

/**
 * 도메인 ↔ JpaEntity 변환. package-private이라 이 패키지 밖에서는 JpaEntity를 직접 다룰 수 없다
 * (persistence-convention.md 1번).
 */
class LiveHighlightMapper {

    private LiveHighlightMapper() {
    }

    static LiveHighlight toDomain(LiveHighlightJpaEntity e) {
        return LiveHighlight.builder()
                .id(e.getId())
                .publicId(e.getPublicId())
                .sessionId(e.getSessionId())
                .kind(e.getKind())
                .sceneLabel(e.getSceneLabel())
                .title(e.getTitle())
                .startSec(e.getStartSec())
                .endSec(e.getEndSec())
                .clipUrl(e.getClipUrl())
                .caption(e.getCaption())
                .isPublic(e.isPublic())
                .generationStatus(e.getGenerationStatus())
                .viewCount(e.getViewCount())
                .clickCount(e.getClickCount())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /** <b>신규 생성 전용</b>이다 — 갱신은 어댑터가 관리 엔티티의 {@code applyFrom}으로 한다. */
    static LiveHighlightJpaEntity toEntity(LiveHighlight h) {
        return LiveHighlightJpaEntity.builder()
                .publicId(h.getPublicId())
                .sessionId(h.getSessionId())
                .kind(h.getKind())
                .sceneLabel(h.getSceneLabel())
                .title(h.getTitle())
                .startSec(h.getStartSec())
                .endSec(h.getEndSec())
                .clipUrl(h.getClipUrl())
                .caption(h.getCaption())
                .isPublic(h.isPublic())
                .generationStatus(h.getGenerationStatus())
                .viewCount(h.getViewCount())
                .clickCount(h.getClickCount())
                .build();
    }
}
