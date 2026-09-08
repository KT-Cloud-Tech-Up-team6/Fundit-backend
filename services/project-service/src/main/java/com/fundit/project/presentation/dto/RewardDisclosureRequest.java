package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RewardDisclosureRequest(
        @NotBlank String categoryType,
        @NotNull Map<String, String> disclosure
) {
}
