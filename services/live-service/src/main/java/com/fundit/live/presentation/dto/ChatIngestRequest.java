package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** IVS Chat Logging(Firehose)이 보내는 적재 페이로드. */
public record ChatIngestRequest(
        @NotBlank String roomArn,
        @NotBlank String ivsMessageId,
        @NotNull UUID senderId,
        @NotBlank String content,
        @NotNull Instant sentAt
) {
}
