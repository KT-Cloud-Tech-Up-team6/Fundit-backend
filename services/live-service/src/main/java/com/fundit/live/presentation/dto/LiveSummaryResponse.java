package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * 목록·배너 공통 항목.
 *
 * <p>{@code title}이 없다 — 요구사항정의서 6.2.4.1의 LIVE 입력 항목은 카테고리·소개 문구·
 * 방송 예정일뿐이고 제목 입력이 없다. 카드에 노출할 문구는 {@code introText}다.
 *
 * <p>{@code viewerCount}도 없다 — DB 컬럼이 아니라 IVS 지표 조회 결과다. IVS 연동이 붙을 때
 * 채운다(지표 조회가 실패하면 필드를 생략하고 목록 자체는 정상 응답한다).
 */
public record LiveSummaryResponse(UUID liveId, String introText, String status, UUID projectId,
                                  String thumbnailUrl, Instant scheduledStartAt, int likeCount,
                                  Instant createdAt) {

    public static LiveSummaryResponse from(LiveSessionJpaEntity e) {
        return new LiveSummaryResponse(e.getPublicId(), e.getIntroText(), e.getStatus().name(),
                e.getProjectId(), e.getThumbnailUrl(), e.getScheduledStartAt(),
                e.getLikeCount(), e.getCreatedAt());
    }
}
