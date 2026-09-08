package com.fundit.order.application.funding;

import java.util.UUID;

/**
 * order-service가 발행하는 펀딩 도메인 이벤트의 아웃바운드 포트(ORDER-006/014).
 * 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다({@code OutboxFundingEventPublisher}).
 * 실제 채널 발행은 워커가 재시도하고, 브로커가 확정되면 {@code FundingEventTransport}
 * 구현체만 교체한다(project-service RewardEventPublisher와 동일 패턴, CLAUDE.md 참고).
 */
public interface FundingEventPublisher {

    void publishFundingGoalFailed(FundingGoalFailedEvent event);

    void publishFundingSucceeded(FundingSucceededEvent event);

    void publishFundingCancelledByMember(FundingCancelledByMemberEvent event);

    record FundingGoalFailedEvent(Long fundingId, Long projectId) {
    }

    record FundingSucceededEvent(Long fundingId, Long projectId) {
    }

    record FundingCancelledByMemberEvent(Long fundingId, Long projectId, UUID memberId) {
    }
}
