package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        @Pattern(regexp = "AMOUNT|RATE", message = "earlyBirdDiscountType은 AMOUNT, RATE 중 하나여야 합니다.")
        String earlyBirdDiscountType,
        @PositiveOrZero Long earlyBirdDiscountValue,
        @Valid List<RewardOptionRequest> options
) {
}
