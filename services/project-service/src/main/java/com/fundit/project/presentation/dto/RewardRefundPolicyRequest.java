package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record RewardRefundPolicyRequest(@NotNull Boolean simpleRefundDisabled) {
}
