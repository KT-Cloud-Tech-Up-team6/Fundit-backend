package com.fundit.project.domain.fundingstatus;

/**
 * 리워드(옵션) 단위 판매 통계. {@code optionValueId}가 null이면 리워드 전체 합계,
 * 있으면 해당 옵션값 한정 통계다(옵션 없는 리워드는 항상 null 한 건).
 *
 * <p>이 구조는 계약만 준비된 상태다 — order-service가 옵션 단위 집계를 담은
 * 펀딩 집계 이벤트(PROJECT-015)를 아직 발행하지 않아, 실제로 채워주는 구독자가 없다
 * (project-service CLAUDE.md "SEARCH-013 최우선 미해결" 참고, 같은 이벤트를 공유한다).
 * 이벤트가 확정되면 이 필드들을 채우는 구독자만 추가하면 된다.
 */
public record RewardStat(Long rewardId, Long optionValueId, Integer purchasedQuantity, Long purchasedAmount) {
}
