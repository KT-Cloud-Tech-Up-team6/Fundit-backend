package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewDecisionRequest(
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED", message = "decision은 APPROVED 또는 REJECTED여야 합니다.")
        String decision,
        String rejectReason
) {
}
