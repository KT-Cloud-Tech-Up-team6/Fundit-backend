package com.fundit.live.presentation.dto;

import com.fundit.live.application.session.LivePlaybackService.Playback;

import java.time.Instant;
import java.util.UUID;

public record PlaybackResponse(UUID liveId, String type, String playbackUrl, UUID projectId,
                               int likeCount, Instant vodReadyAt) {

    public static PlaybackResponse from(Playback p) {
        return new PlaybackResponse(p.liveId(), p.type(), p.playbackUrl(), p.projectId(),
                p.likeCount(), p.vodReadyAt());
    }
}
