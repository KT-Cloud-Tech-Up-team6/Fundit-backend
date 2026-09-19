package com.fundit.live.presentation.dto;

import com.fundit.live.domain.session.LiveSession;

import java.util.UUID;

public record LiveCreateResponse(UUID liveId, String status) {

    public static LiveCreateResponse from(LiveSession session) {
        return new LiveCreateResponse(session.getPublicId(), session.getStatus().name());
    }
}
