package com.fundit.project.presentation.dto.reward;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RewardOptionCreateRequest(@NotBlank String optionName,
                                        @NotBlank String sku,
                                        @NotNull Integer initialStock) {
}
