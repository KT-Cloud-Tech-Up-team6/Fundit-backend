package com.fundit.live.presentation.dto;

import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.ProjectClip;

import java.time.Instant;
import java.util.UUID;

/**
 * 프로젝트 단위 공개 숏 클립. 공개·생성 완료분만 나가므로 {@code isPublic}·{@code generationStatus}는 싣지 않는다.
 * 여러 방송의 클립이 섞이므로 {@code liveId}를 같이 준다.
 */
public record ProjectClipResponse(UUID liveId, UUID highlightId, String sceneLabel, String title,
                                  int startSec, Integer endSec, String clipUrl, String thumbnailUrl,
                                  String caption, Instant createdAt) {

    public static ProjectClipResponse from(ProjectClip projectClip) {
        LiveHighlight h = projectClip.clip();
        return new ProjectClipResponse(projectClip.liveId(), h.getPublicId(), h.getSceneLabel().name(), h.getTitle(),
                h.getStartSec(), h.getEndSec(), h.getClipUrl(), h.getThumbnailUrl(), h.getCaption(), h.getCreatedAt());
    }
}
