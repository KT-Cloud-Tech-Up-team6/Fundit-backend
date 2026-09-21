package com.fundit.order.application.funding;

import java.util.List;
import java.util.UUID;

/**
 * PROJECT-015 — 리워드 단위 펀딩 구매 통계를 project-service에 통지하는 아웃바운드 포트
 * (`project.funding-reward-stats-updated.v1`). PRD 7.1.3이 "데이터 갱신 주기 24시간"을
 * 명시해 실시간 반영 없이 하루 한 번 배치로 계산·발행한다({@link FundingRewardStatsBatchService}).
 *
 * <p>호출부는 같은 트랜잭션에서 아웃박스에만 적재한다.
 * {@code optionValueId}가 null이면 <b>리워드 전체 합계</b>(옵션이 있는 리워드도 항상 이 행을 포함),
 * 있으면 해당 옵션값 한정 통계다. 옵션이 여러 그룹(색상+사이즈 등)이면 라인 하나가 옵션값 행을
 * 여러 개 만들어 각 행이 라인 전체 수량을 그대로 갖는다 — 총 판매량은 이 옵션값 행들을 합산하지 말고
 * optionValueId=null 행을 그대로 쓸 것.
 */
public interface FundingRewardStatsPublisher {

    void publishRewardStatsUpdated(RewardStatsUpdatedEvent event);

    record RewardStatItem(Long rewardId, Long optionValueId, int purchasedQuantity, long purchasedAmount) {
    }

    /**
     * {@code projectId}는 project-service의 publicId(UUID)다 — order-service는 project-service의
     * 내부 Long PK를 알 방법이 없어(cross-service ID 통일 #69), UUID로 발행하고 project-service가
     * 자기 DB에서 {@code publicId → 내부 id}로 스스로 변환한다.
     */
    record RewardStatsUpdatedEvent(UUID projectId, List<RewardStatItem> rewardStats) {
    }
}
