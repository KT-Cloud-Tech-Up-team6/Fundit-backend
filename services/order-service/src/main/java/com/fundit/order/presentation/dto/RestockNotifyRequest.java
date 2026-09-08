package com.fundit.order.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record RestockNotifyRequest(@NotNull Long rewardId) {
}
