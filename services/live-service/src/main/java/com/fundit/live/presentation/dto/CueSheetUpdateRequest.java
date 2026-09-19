package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * 판매자 직접 수정 요청. 본문은 {@code { "segments": [ ... ] }}다(API 명세서).
 *
 * <p>이전에는 {@code @RequestBody String}으로 본문 전체를 받아 그대로 저장했다 —
 * 빈 문자열도 깨진 JSON도 통과해 조회 시점에 프론트에서 터졌다.
 *
 * <p><b>구간 스키마를 record로 못 박지 않는 이유</b>: 구간 구조는 AI 계약이고 아직 확정 전이다.
 * 지금 타입으로 박으면 AI가 필드 하나 늘릴 때마다 우리가 배포해야 한다. JSON 문법 유효성은
 * Jackson이 파싱 단계에서 보고(깨졌으면 400), 비어 있는지만 여기서 본다.
 */
public record CueSheetUpdateRequest(@NotNull JsonNode segments) {

    @AssertTrue(message = "구간은 비어 있지 않은 배열이어야 합니다.")
    public boolean isSegmentsArray() {
        return segments != null && segments.isArray() && !segments.isEmpty();
    }

    /** 저장·응답 모두 배열 문자열이다 — AI가 밀어주는 값과 같은 모양이어야 한다. */
    public String segmentsJson() {
        return segments.toString();
    }
}
