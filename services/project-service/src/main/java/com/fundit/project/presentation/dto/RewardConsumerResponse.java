package com.fundit.project.presentation.dto;

import java.util.List;

public record RewardConsumerResponse(
        Long rewardId, String rewardDisplayCode, String name, String description, String imageUrl,
        Long price, boolean isEarlyBird,
        String earlyBirdDiscountType, Long earlyBirdDiscountValue, Long earlyBirdDiscountedPrice,
        boolean isLimited, Integer remainingStock, List<RewardOptionGroupResponse> options, boolean soldOut) {
}
