package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * {@code mode}는 SCENARIO(진행 순서 + 구간별 주요 내용 + 예상 시간 + 핵심 포인트) 또는
 * SCRIPT(위 전부 + 구간별 완성 대사)다(요구사항정의서 6.2.4.2).
 */
public record CueSheetGenerateRequest(
        @NotBlank String mode,
        @Positive int targetDurationSec,
        // Boolean(박싱)인 이유: 선택 필드인데 primitive면 Jackson 3이 값 누락을 400으로 떨군다.
        // FE가 쓰지 않는 필드까지 전부 채워 보내야 하는 API가 된다.
        Boolean demoAvailable,
        List<String> emphasisPoints,
        String tone,
        List<String> mandatoryPhrases
) {
    public boolean demoAvailableOrFalse() {
        return Boolean.TRUE.equals(demoAvailable);
    }

    public List<String> emphasisOrEmpty() {
        return emphasisPoints == null ? List.of() : emphasisPoints;
    }

    public List<String> mandatoryOrEmpty() {
        return mandatoryPhrases == null ? List.of() : mandatoryPhrases;
    }
}
