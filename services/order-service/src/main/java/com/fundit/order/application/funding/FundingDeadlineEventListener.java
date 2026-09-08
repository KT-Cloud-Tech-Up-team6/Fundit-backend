package com.fundit.order.application.funding;

/**
 * ORDER-006 — 펀딩 마감 목표달성 판정의 인바운드 트리거. order-service는 목표금액(goal_amount)을
 * 알지 못한다(project-service 소유) — project-service가 마감 시각 도래를 감지해 이 포트를
 * 호출(또는 이벤트 발행)하며 goalAmount를 함께 넘겨준다고 가정한다[가정 — project-service 쪽
 * "마감 도래" 트리거 구현이 아직 없어, 실제 배선(스케줄러/이벤트 구독)은 프로젝트-service 담당자와
 * 협의 후 추가해야 한다. 판정 로직 자체(FundingGoalJudgmentService)는 이 포트만 호출하면
 * 바로 동작하도록 완성해둔다 — ORDER-016 RewardEventListener와 동일한 "골격 우선" 접근].
 */
public interface FundingDeadlineEventListener {

    void onProjectFundingDeadlineReached(ProjectFundingDeadlineReachedEvent event);

    record ProjectFundingDeadlineReachedEvent(Long projectId, long goalAmount) {
    }
}
