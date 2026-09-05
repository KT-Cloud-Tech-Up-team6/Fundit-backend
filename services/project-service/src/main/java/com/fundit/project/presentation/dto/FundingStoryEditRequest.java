package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record FundingStoryEditRequest(@NotBlank String sectionType, @NotBlank String body) {
}
