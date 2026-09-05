package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RewardOptionRequest(
        @NotBlank String groupName,
        @NotEmpty List<@NotBlank String> values
) {
}
