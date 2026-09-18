package com.fundit.payment.application.refund;

import java.util.UUID;

/**
 * 이 서비스가 order-service로부터 구독하는 펀딩 이벤트의 인바운드 포트(PAYMENT-004/005).
 * 필드 구성은 order-service {@code FundingEventPublisher}의 {@code FundingCancelledByMemberEvent}/
 * {@code FundingGoalFailedEvent}와 동일하고, cross-service ID 통일(#69)로 {@code orderId}(UUID)가
 * 마지막 컴포넌트로 추가됐다(필드 추가, .v1 토픽 유지).
 */
public interface FundingLifecycleEventListener {

    void onFundingCancelledByMember(FundingCancelledByMemberEvent event);

    /** PAYMENT-005 — 펀딩 1건당 1개씩 발행된다. 프로젝트 단위 일괄 처리로 짜지 않는다. */
    void onFundingGoalFailed(FundingGoalFailedEvent event);

    record FundingCancelledByMemberEvent(Long fundingId, Long projectId, UUID memberId, UUID orderId) {
    }

    record FundingGoalFailedEvent(Long fundingId, Long projectId, UUID orderId) {
    }
}
