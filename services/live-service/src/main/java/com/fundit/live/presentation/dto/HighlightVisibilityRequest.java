package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** {@code isPublic}을 Boolean으로 받는다 — primitive면 값 누락이 400이 된다. */
public record HighlightVisibilityRequest(@NotNull Boolean isPublic) {
}
