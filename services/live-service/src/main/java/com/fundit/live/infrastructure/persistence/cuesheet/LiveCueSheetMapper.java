package com.fundit.live.infrastructure.persistence.cuesheet;

import com.fundit.live.domain.cuesheet.LiveCueSheet;

/** 도메인 ↔ JpaEntity 변환. package-private이다(persistence-convention.md 1번). */
class LiveCueSheetMapper {

    private LiveCueSheetMapper() {
    }

    static LiveCueSheet toDomain(LiveCueSheetJpaEntity e) {
        return LiveCueSheet.builder()
                .sessionId(e.getSessionId())
                .mode(e.getMode())
                .status(e.getStatus())
                .targetDurationSec(e.getTargetDurationSec())
                .segments(e.getSegments())
                .failureReason(e.getFailureReason())
                .build();
    }

    static LiveCueSheetJpaEntity toEntity(LiveCueSheet c) {
        return LiveCueSheetJpaEntity.builder()
                .sessionId(c.getSessionId())
                .mode(c.getMode())
                .status(c.getStatus())
                .targetDurationSec(c.getTargetDurationSec())
                .segments(c.getSegments())
                .failureReason(c.getFailureReason())
                .build();
    }
}
