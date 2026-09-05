package com.fundit.project.presentation.dto;

public record RewardResponse(
        Long rewardId,
        String rewardDisplayCode,
        String name,
        Long price,
        boolean isLimited,
        Integer quantity,
        boolean hasOption
) {
}
