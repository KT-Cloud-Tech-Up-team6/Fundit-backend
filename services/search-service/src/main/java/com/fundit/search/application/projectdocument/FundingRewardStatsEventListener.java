package com.fundit.search.application.projectdocument;

import java.util.List;
import java.util.UUID;

/**
 * SEARCH-013 인바운드 포트 — order-service가 이미 발행 중인
 * {@code project.funding-reward-stats-updated.v1}을 재사용한다(새 이벤트 신설 불필요).
 * project-service {@code ProjectFundingRewardStatsUpdatedEvent}와 동일 계약이다
 * (event-convention.md 4번 — JSON이 계약, 레코드는 서비스마다 따로 선언).
 */
public interface FundingRewardStatsEventListener {

    void onRewardStatsUpdated(RewardStatsUpdatedEvent event);

    /**
     * {@code projectId}는 project-service의 publicId(UUID)다 — order-service는 내부 PK를 모른다.
     *
     * <p>{@code participantCount}가 박싱인 이유: 필드가 없던 구버전 order 메시지는 null로 들어와
     * 기존 값을 지켜야 한다 — primitive면 0으로 덮인다.
     */
    record RewardStatsUpdatedEvent(UUID projectId, List<RewardStat> rewardStats, Integer participantCount) {
    }

    /**
     * {@code optionValueId}가 null이면 리워드 전체 합계 행(옵션이 있는 리워드도 항상 이 행이 온다),
     * 있으면 해당 옵션값 한정 통계다 — 모금액은 null 행만 더한다(옵션 행까지 더하면 중복 계상).
     */
    record RewardStat(Long rewardId, Long optionValueId, Integer purchasedQuantity, Long purchasedAmount) {
    }
}
