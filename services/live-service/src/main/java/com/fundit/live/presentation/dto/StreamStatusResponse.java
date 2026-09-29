package com.fundit.live.presentation.dto;

import com.fundit.live.application.ivs.IvsClient;

import java.time.Instant;

/** 판매자 송출 화면의 송출 상태. 방송이 안 들어오면 {@code state=OFFLINE}, 나머지는 null·0이다. */
public record StreamStatusResponse(String state, String health, int viewerCount, Instant startedAt) {

    public static StreamStatusResponse from(IvsClient.StreamStatus status) {
        return new StreamStatusResponse(status.state(), status.health(), status.viewerCount(), status.startedAt());
    }
}
