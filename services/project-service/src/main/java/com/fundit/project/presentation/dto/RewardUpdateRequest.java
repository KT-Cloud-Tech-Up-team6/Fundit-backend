package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** PATCH .../rewards/{id} — 등록과 동일한 필드 중 변경할 필드만 부분 전달한다. */
public record RewardUpdateRequest(
        String name,
        String description,
        String imageUrl,
        @PositiveOrZero Long price,
        Boolean isLimited,
        @PositiveOrZero Integer quantity,
        Boolean isEarlyBird,
        @Valid List<RewardOptionRequest> options
) {
}
