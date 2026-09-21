package com.fundit.project.domain.fundingstatus;

/**
 * 리워드(옵션) 단위 판매 통계. {@code optionValueId}가 null이면 옵션 없는 리워드 합계,
 * 있으면 해당 옵션값 한정 통계다.
 */
public record RewardStat(Long rewardId, Long optionValueId, Integer purchasedQuantity, Long purchasedAmount) {
}
