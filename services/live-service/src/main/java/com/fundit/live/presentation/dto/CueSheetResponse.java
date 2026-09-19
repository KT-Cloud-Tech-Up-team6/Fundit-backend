package com.fundit.live.presentation.dto;

import com.fundit.live.domain.cuesheet.LiveCueSheet;

/** {@code segments}는 JSON 문자열 그대로 내려준다 — 서버가 해석할 이유가 없다. */
public record CueSheetResponse(String status, String mode, int totalDurationSec,
                               String segments, String failureReason) {

    public static CueSheetResponse from(LiveCueSheet c) {
        return new CueSheetResponse(c.getStatus().name(), c.getMode(), c.getTargetDurationSec(),
                c.getSegments(), c.getFailureReason());
    }
}
