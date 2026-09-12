package com.fundit.fulfillment.application.funding;

/**
 * order-service가 아웃박스로 발행하는 펀딩 성립 이벤트를 처리하는 인바운드 포트(FULFILLMENT-001).
 * 이벤트 필드는 order-service {@code FundingEventPublisher.FundingSucceededEvent}와
 * 동일한 계약이다(fundingId, projectId).
 *
 * <p>메시지 브로커가 아직 확정되지 않아 이 포트를 호출하는 실제 리스너 어댑터(예: @KafkaListener)는
 * 없다 — order-service {@code RewardEventListener}, payment-service
 * {@code FundingLifecycleEventListener}와 동일한 상황이며, 브로커가 정해지면
 * infrastructure/event에 어댑터만 추가해 이 포트를 호출하면 된다. 그 전까지는 구현체
 * ({@code FulfillmentTrackerInitializationService})를 직접 호출/테스트할 수 있는 형태로
 * 완성해둔다(CLAUDE.md "구현 순서 권장" 2번 참고).
 */
public interface FundingSuccessEventListener {

    void onFundingSucceeded(FundingSucceededEvent event);

    record FundingSucceededEvent(Long fundingId, Long projectId) {
    }
}
