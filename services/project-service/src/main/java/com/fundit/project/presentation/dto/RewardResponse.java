package com.fundit.project.presentation.dto;

import java.util.List;

public record RewardResponse(
        Long rewardId,
        String rewardDisplayCode,
        String name,
        String description,
        String imageUrl,
        Long price,
        boolean isLimited,
        Integer quantity,
        boolean hasOption,
        int sortOrder,
        boolean isEarlyBird,
        String earlyBirdDiscountType,
        Long earlyBirdDiscountValue,
        Long earlyBirdDiscountedPrice,
        Long shippingFee,
        Integer estimatedDeliveryDays,
        boolean simpleRefundDisabled,
        List<RewardOptionGroupResponse> options
) {
}
