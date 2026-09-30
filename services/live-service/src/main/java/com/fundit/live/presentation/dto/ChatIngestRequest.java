package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** 채팅 단건 적재 페이로드(내부 테스트 경로). 실서버 적재는 채팅방 구독이 한다. */
public record ChatIngestRequest(
        @NotBlank String roomArn,
        @NotBlank String ivsMessageId,
        @NotNull UUID senderId,
        @NotBlank String content,
        @NotNull Instant sentAt
) {
}
