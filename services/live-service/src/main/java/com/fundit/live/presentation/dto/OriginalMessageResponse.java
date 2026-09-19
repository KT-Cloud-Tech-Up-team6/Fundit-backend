package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * 대표질문 원본 채팅(요구사항정의서 6.4.4.3).
 *
 * <p>다시보기 채팅 응답을 재사용하지 않는다 — 그쪽의 {@code offsetSec}(방송 시작 기준 경과 초)이
 * 여기선 채울 값이 없어 전부 0이 되고, 프론트가 원본 댓글을 전부 0초에 표시하게 된다.
 * 이 화면에 필요한 건 재생 위치가 아니라 <b>보낸 시각</b>이다.
 */
public record OriginalMessageResponse(UUID senderId, String content, Instant sentAt) {

    public static OriginalMessageResponse from(ChatMessageJpaEntity e) {
        return new OriginalMessageResponse(e.getSenderId(), e.getContent(), e.getSentAt());
    }
}
