package com.fundit.live.presentation.dto;

import com.fundit.live.domain.highlight.LiveHighlight;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 마커와 클립은 한 테이블에 저장하고 <b>응답만 두 배열로 나눈다</b> —
 * 화면이 타임라인과 쇼츠를 따로 그린다.
 */
public record HighlightResponse(List<Item> markers, List<Item> clips) {

    public record Item(UUID highlightId, String sceneLabel, String title, int startSec, Integer endSec,
                       String clipUrl, String thumbnailUrl, String caption, boolean isPublic,
                       String generationStatus, Instant createdAt) {

        public static Item from(LiveHighlight h) {
            return new Item(h.getPublicId(), h.getSceneLabel().name(), h.getTitle(), h.getStartSec(),
                    h.getEndSec(), h.getClipUrl(), h.getThumbnailUrl(), h.getCaption(), h.isPublic(),
                    h.getGenerationStatus().name(), h.getCreatedAt());
        }
    }

    public static HighlightResponse from(List<LiveHighlight> all) {
        return new HighlightResponse(
                all.stream().filter(h -> !h.isClip()).map(Item::from).toList(),
                all.stream().filter(LiveHighlight::isClip).map(Item::from).toList());
    }
}
