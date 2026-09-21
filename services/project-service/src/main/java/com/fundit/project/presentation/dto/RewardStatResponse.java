package com.fundit.project.presentation.dto;

/** optionValueId가 null이면 리워드 전체 합계, 있으면 해당 옵션값 한정 통계다. */
public record RewardStatResponse(Long rewardId, Long optionValueId, Integer purchasedQuantity, Long purchasedAmount) {
}
