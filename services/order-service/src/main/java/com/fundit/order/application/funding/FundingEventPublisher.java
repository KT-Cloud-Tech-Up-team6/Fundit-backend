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

    /**
     * 결제는 완료됐는데 주문이 이미 만료·취소돼 결제를 받을 수 없는 상태다. payment-service(PAYMENT-017)가
     * 받아 전액 환불한다 — 돈만 빠지고 주문은 무효로 남는 사고를 막는다.
     */
    void publishPaymentReconciliationRequired(PaymentReconciliationRequiredEvent event);

    record FundingGoalFailedEvent(Long fundingId, Long projectId) {
    }

    record FundingSucceededEvent(Long fundingId, Long projectId) {
    }

    record FundingCancelledByMemberEvent(Long fundingId, Long projectId, UUID memberId) {
    }

    record PaymentReconciliationRequiredEvent(Long fundingId) {
    }
}
