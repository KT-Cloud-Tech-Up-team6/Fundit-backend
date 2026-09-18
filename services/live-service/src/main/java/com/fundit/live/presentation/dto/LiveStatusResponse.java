package com.fundit.live.presentation.dto;

import com.fundit.live.domain.session.LiveSession;

import java.time.Instant;
import java.util.UUID;

/** 설정 저장·시작·종료의 공통 응답. 상태와 시각만 돌려준다. */
public record LiveStatusResponse(UUID liveId, String status, Instant scheduledStartAt,
                                 Instant actualStartAt, Instant actualEndAt) {

    public static LiveStatusResponse from(LiveSession session) {
        return new LiveStatusResponse(session.getPublicId(), session.getStatus().name(),
                session.getScheduledStartAt(), session.getActualStartAt(), session.getActualEndAt());
    }
}
