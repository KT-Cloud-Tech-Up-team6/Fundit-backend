package com.fundit.project.application.project;

/**
 * 펀딩 마감 도래를 order-service에 통지하기 위한 아웃바운드 포트(`event-convention.md`
 * `project.funding-deadline-reached.v1`). order-service의 {@code FundingGoalJudgmentService}가
 * 이 이벤트를 트리거로 목표 달성/미달을 판정하므로, 여기 없으면 그 파이프라인 전체가 동작하지 않는다.
 *
 * 호출부는 같은 트랜잭션에서 아웃박스에 적재한다({@code OutboxFundingDeadlinePublisher}).
 * 이벤트 레코드는 order-service {@code FundingDeadlineEventListener.ProjectFundingDeadlineReachedEvent}와
 * 서비스 간에 공유하지 않고 필드 계약만 맞춘다(event-convention.md 4번).
 */
public interface FundingDeadlinePublisher {

    void publishFundingDeadlineReached(FundingDeadlineReachedEvent event);

    /** projectId는 project-service 내부 Long PK(파티션 키), goalAmount는 판정 시점 목표금액 스냅샷. */
    record FundingDeadlineReachedEvent(Long projectId, long goalAmount) {
    }
}
