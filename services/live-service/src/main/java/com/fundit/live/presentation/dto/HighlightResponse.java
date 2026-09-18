package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;

import java.util.List;
import java.util.UUID;

/**
 * 마커와 클립은 한 테이블에 저장하고 <b>응답만 두 배열로 나눈다</b> —
 * 화면이 타임라인과 쇼츠를 따로 그린다.
 */
public record HighlightResponse(List<Item> markers, List<Item> clips) {

    public record Item(UUID highlightId, String sceneLabel, String title, int startSec, Integer endSec,
                       String clipUrl, String caption, boolean isPublic, String generationStatus) {

        public static Item from(LiveHighlightJpaEntity e) {
            return new Item(e.getPublicId(), e.getSceneLabel(), e.getTitle(), e.getStartSec(),
                    e.getEndSec(), e.getClipUrl(), e.getCaption(), e.isPublic(), e.getGenerationStatus());
        }
    }

    public static HighlightResponse from(List<LiveHighlightJpaEntity> all) {
        return new HighlightResponse(
                all.stream().filter(e -> LiveHighlightJpaEntity.KIND_MARKER.equals(e.getKind()))
                        .map(Item::from).toList(),
                all.stream().filter(e -> LiveHighlightJpaEntity.KIND_CLIP.equals(e.getKind()))
                        .map(Item::from).toList());
    }
}
