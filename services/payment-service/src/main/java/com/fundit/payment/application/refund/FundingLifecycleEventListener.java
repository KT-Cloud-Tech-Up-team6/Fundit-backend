package com.fundit.payment.application.refund;

import java.util.UUID;

/**
 * 이 서비스가 order-service로부터 구독하는 펀딩 이벤트의 인바운드 포트(PAYMENT-004/005).
 * 필드 구성은 order-service {@code FundingEventPublisher}의 {@code FundingCancelledByMemberEvent}/
 * {@code FundingGoalFailedEvent}와 동일하다(order-service가 발행 주체이므로 그 형태를 그대로 따른다).
 *
 * <p>브로커가 아직 없어(레포 공통 상황) 이 인터페이스를 실제로 호출하는 어댑터는 아직 없다.
 * 브로커가 확정되면 리스너 어댑터 하나만 추가하면 된다(payment-service CLAUDE.md "공통 원칙").
 */
public interface FundingLifecycleEventListener {

    void onFundingCancelledByMember(FundingCancelledByMemberEvent event);

    /** PAYMENT-005 — 펀딩 1건당 1개씩 발행된다. 프로젝트 단위 일괄 처리로 짜지 않는다. */
    void onFundingGoalFailed(FundingGoalFailedEvent event);

    record FundingCancelledByMemberEvent(Long fundingId, Long projectId, UUID memberId) {
    }

    record FundingGoalFailedEvent(Long fundingId, Long projectId) {
    }
}
