package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.Size;

/** 부분 수정 — null인 필드는 건드리지 않는다. */
public record HighlightEditRequest(Integer startSec, Integer endSec, String sceneLabel,
                                   @Size(max = 100) String title, String caption) {
}
