package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * 단건 상세 — 임시저장 불러오기, 설정 화면 재진입, 방송 중 화면 공통(FE 요청).
 * 소유자 전용이라 {@link LiveSummaryResponse}(목록·소비자용)와 달리 DRAFT도 그대로 내려간다.
 *
 * <p>{@code viewerCount}·{@code elapsedSeconds}는 {@code status=LIVE}일 때만 채워진다 — DB 컬럼이
 * 아니라 IVS 실시간 조회·{@code actualStartAt} 계산값이라 방송 중이 아니면 의미가 없다.
 */
public record LiveDetailResponse(UUID liveId, String status, UUID projectId,
                                 String categoryMajor, String categoryMinor, String introText,
                                 String thumbnailUrl, Instant scheduledStartAt, int likeCount,
                                 Instant createdAt, Integer viewerCount, Long elapsedSeconds) {

    public static LiveDetailResponse from(LiveSessionJpaEntity e, Integer viewerCount, Long elapsedSeconds) {
        return new LiveDetailResponse(e.getPublicId(), e.getStatus().name(), e.getProjectId(),
                e.getCategoryMajor(), e.getCategoryMinor(), e.getIntroText(), e.getThumbnailUrl(),
                e.getScheduledStartAt(), e.getLikeCount(), e.getCreatedAt(), viewerCount, elapsedSeconds);
    }
}
