package com.fundit.live.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundit.live.application.session.LiveStatusQueryService;

import java.util.UUID;

/**
 * order-service가 라이브 쿠폰 발급·주문 집계 전에 방송 상태를 확인하는 응답.
 * 호출 방향은 order → live다 — 쿠폰의 주인이 order이므로 판정 정보를 그쪽이 가져간다.
 *
 * <p>{@code ALWAYS}: 서비스 기본값이 {@code non_null}이라 그대로 두면 "없음"이 {@code {}}로 나간다.
 * order와 "없으면 전부 null"로 합의했으니 필드를 명시적으로 내보낸다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InternalLiveStatusResponse(UUID liveId, Long sessionId, String status, UUID sellerId) {

    public static InternalLiveStatusResponse from(LiveStatusQueryService.LiveStatus s) {
        return new InternalLiveStatusResponse(s.liveId(), s.sessionId(), s.status(), s.sellerId());
    }

    /** 해당 세션 없음. 404가 아니라 200 + 전부 null — order가 "없음(정상)"과 "호출 실패"를 구분한다. */
    public static InternalLiveStatusResponse empty() {
        return new InternalLiveStatusResponse(null, null, null, null);
    }
}
