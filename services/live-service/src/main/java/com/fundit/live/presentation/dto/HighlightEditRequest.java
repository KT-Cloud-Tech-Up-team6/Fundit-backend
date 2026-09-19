package com.fundit.live.presentation.dto;

import com.fundit.live.domain.highlight.SceneLabel;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부분 수정 — null인 필드는 건드리지 않는다.
 *
 * <p>음수는 여기서 막는다. 도메인도 같은 검사를 하지만 그건 <b>마지막 방어선</b>이고,
 * 경계에서 걸러야 400으로 나간다(S2).
 */
public record HighlightEditRequest(@PositiveOrZero Integer startSec, @PositiveOrZero Integer endSec,
                                   SceneLabel sceneLabel, @Size(max = 100) String title, String caption) {
}
