package com.fundit.live.presentation.dto;

import com.fundit.live.application.session.LiveStreamService;

/** 판매자가 OBS 같은 송출 프로그램에 넣을 값. 소유자 전용 응답이다. */
public record StreamInfoResponse(String ingestEndpoint, String streamKey) {

    public static StreamInfoResponse from(LiveStreamService.StreamInfo info) {
        return new StreamInfoResponse(info.ingestEndpoint(), info.streamKey());
    }
}
