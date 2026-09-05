package com.fundit.project.presentation.dto;

import java.util.List;

public record RewardConsumerResponse(
        Long rewardId, String rewardDisplayCode, String name, Long price, boolean isEarlyBird,
        boolean isLimited, Integer remainingStock, List<RewardOptionGroupResponse> options, boolean soldOut) {
}
