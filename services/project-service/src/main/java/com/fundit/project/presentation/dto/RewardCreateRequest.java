package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record RewardCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        String imageUrl,
        @NotNull @PositiveOrZero Long price,
        @NotNull Boolean isLimited,
        @PositiveOrZero Integer quantity,
        Boolean isEarlyBird,
        @Valid List<RewardOptionRequest> options
) {
}
