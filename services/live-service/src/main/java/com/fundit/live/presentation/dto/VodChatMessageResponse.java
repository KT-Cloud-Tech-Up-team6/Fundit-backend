package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code offsetSec}는 방송 시작 기준 경과 초다 — 클라이언트가 재생 위치에 맞춰 띄운다.
 * {@code messageId}는 IVS 메시지 {@code Id}와 같은 값이라, LIVE 중 입장 시 실시간 메시지와 중복을 거를 수 있다.
 * {@code nickname}은 조회 실패·탈퇴 회원이면 null이다.
 */
public record VodChatMessageResponse(String messageId, UUID senderId, String nickname, String content, long offsetSec) {

    public static VodChatMessageResponse from(ChatMessageJpaEntity e, Instant base, String nickname) {
        return new VodChatMessageResponse(e.getIvsMessageId(), e.getSenderId(), nickname, e.getContent(),
                Duration.between(base, e.getSentAt()).toSeconds());
    }
}
