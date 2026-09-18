package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaEntity;

/** {@code segments}는 JSON 문자열 그대로 내려준다 — 서버가 해석할 이유가 없다. */
public record CueSheetResponse(String status, String mode, int totalDurationSec,
                               String segments, String failureReason) {

    public static CueSheetResponse from(LiveCueSheetJpaEntity e) {
        return new CueSheetResponse(e.getStatus(), e.getMode(), e.getTargetDurationSec(),
                e.getSegments(), e.getFailureReason());
    }
}
