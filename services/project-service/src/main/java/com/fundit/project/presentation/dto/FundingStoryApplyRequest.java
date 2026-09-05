package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record FundingStoryApplyRequest(
        @NotBlank
        @Pattern(regexp = "OVERWRITE|COPY", message = "mode는 OVERWRITE 또는 COPY여야 합니다.")
        String mode,
        @Valid List<FundingStoryEditRequest> edits
) {
}
