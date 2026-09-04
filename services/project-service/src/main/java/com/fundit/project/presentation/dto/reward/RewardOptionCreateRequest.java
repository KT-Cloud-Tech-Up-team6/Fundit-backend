package com.fundit.project.presentation.dto.reward;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RewardOptionCreateRequest(@NotBlank String optionName,
                                        @NotBlank String sku,
                                        @NotNull @PositiveOrZero Integer initialStock) {
}
