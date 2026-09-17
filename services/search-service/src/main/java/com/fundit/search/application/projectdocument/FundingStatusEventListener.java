package com.fundit.search.application.projectdocument;

/**
 * SEARCH-012 인바운드 포트 — order-service가 이미 발행 중인 기존 이벤트를 재사용한다
 * (새 이벤트 신설 불필요, SearchERD.md 5-① 대상인 SEARCH-011과 다른 점).
 */
public interface FundingStatusEventListener {

    void onFundingSucceeded(FundingSucceededEvent event);

    void onFundingGoalFailed(FundingGoalFailedEvent event);

    /** order-service {@code FundingEventPublisher}와 동일 계약(event-convention.md 4번 — JSON이 계약). */
    record FundingSucceededEvent(Long fundingId, Long projectId) {
    }

    record FundingGoalFailedEvent(Long fundingId, Long projectId) {
    }
}
