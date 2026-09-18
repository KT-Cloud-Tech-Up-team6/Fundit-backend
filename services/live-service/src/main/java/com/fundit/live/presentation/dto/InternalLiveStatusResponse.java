package com.fundit.live.presentation.dto;

import java.util.UUID;

/**
 * order-service가 라이브 쿠폰 발급 전 "이 방송이 진행 중인가"를 확인하는 응답.
 * 호출 방향은 order → live다 — 쿠폰의 주인이 order이므로 판정 정보를 그쪽이 가져간다.
 */
public record InternalLiveStatusResponse(UUID liveId, Long sessionId, String status, UUID sellerId) {
}
