package com.fundit.project.domain.fundingstatus;

/**
 * 리워드(옵션) 단위 판매 통계. {@code optionValueId}가 null이면 <b>리워드 전체 합계</b>
 * (옵션이 있는 리워드도 항상 이 행이 온다), 있으면 해당 옵션값 한정 통계다. 총 판매량이
 * 필요하면 optionValueId=null 행을 쓴다 — 옵션값별 행들을 합산하면 안 된다(옵션 그룹이
 * 2개 이상인 리워드는 라인 하나가 옵션값 행을 여러 개 만들어 합산 시 부풀려진다).
 */
public record RewardStat(Long rewardId, Long optionValueId, Integer purchasedQuantity, Long purchasedAmount) {
}
