package com.fundit.project.presentation.dto.supporterreview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupporterReviewCreateRequest(@NotNull Long fundingId, @NotBlank String content) {
}
