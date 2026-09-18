package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** {@code offsetSec}는 방송 시작 기준 경과 초다 — 클라이언트가 재생 위치에 맞춰 띄운다. */
public record VodChatMessageResponse(UUID senderId, String content, long offsetSec) {

    public static VodChatMessageResponse from(ChatMessageJpaEntity e, Instant base) {
        return new VodChatMessageResponse(e.getSenderId(), e.getContent(),
                Duration.between(base, e.getSentAt()).toSeconds());
    }
}
