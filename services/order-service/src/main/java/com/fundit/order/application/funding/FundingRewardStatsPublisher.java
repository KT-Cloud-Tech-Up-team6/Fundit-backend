package com.fundit.order.application.funding;

import java.util.List;

/**
 * PROJECT-015 — 리워드 단위 펀딩 구매 통계를 project-service에 통지하는 아웃바운드 포트
 * (`project.funding-reward-stats-updated.v1`). PRD 7.1.3이 "데이터 갱신 주기 24시간"을
 * 명시해 실시간 반영 없이 하루 한 번 배치로 계산·발행한다({@link FundingRewardStatsBatchService}).
 *
 * <p>호출부는 같은 트랜잭션에서 아웃박스에만 적재한다.
 * {@code optionValueId}가 null이면 옵션 없는 리워드 합계, 있으면 해당 옵션값 한정 통계다.
 */
public interface FundingRewardStatsPublisher {

    void publishRewardStatsUpdated(RewardStatsUpdatedEvent event);

    record RewardStatItem(Long rewardId, Long optionValueId, int purchasedQuantity, long purchasedAmount) {
    }

    record RewardStatsUpdatedEvent(Long projectId, List<RewardStatItem> rewardStats) {
    }
}
