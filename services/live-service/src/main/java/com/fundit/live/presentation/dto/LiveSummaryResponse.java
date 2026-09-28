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
 * <p>{@code viewerCount}는 {@code sort=viewerCount}(실시간 순위)일 때만 채워진다 — DB 컬럼이
 * 아니라 IVS 실시간 조회 결과라 다른 정렬에서는 세션마다 IVS를 부를 이유가 없다.
 *
 * <p>{@code sellerNickname}은 member-service에서 받아온 판매자 닉네임이다. 소비자 목록·배너만 채우고,
 * member 조회가 실패하거나 닉네임이 없으면 비어 있다(필드 생략) — 카드 목록 자체는 그대로 나간다.
 */
public record LiveSummaryResponse(UUID liveId, String introText, String status, UUID projectId,
                                  String thumbnailUrl, Instant scheduledStartAt, int likeCount,
                                  Instant createdAt, Integer viewerCount, String sellerNickname) {

    public static LiveSummaryResponse from(LiveSessionJpaEntity e) {
        return from(e, null, null);
    }

    public static LiveSummaryResponse from(LiveSessionJpaEntity e, Integer viewerCount, String sellerNickname) {
        return new LiveSummaryResponse(e.getPublicId(), e.getIntroText(), e.getStatus().name(),
                e.getProjectId(), e.getThumbnailUrl(), e.getScheduledStartAt(),
                e.getLikeCount(), e.getCreatedAt(), viewerCount, sellerNickname);
    }
}
