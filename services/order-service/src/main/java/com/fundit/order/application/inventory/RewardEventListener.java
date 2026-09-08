package com.fundit.order.application.inventory;

/**
 * project-service가 아웃박스로 발행하는 리워드 수량 이벤트를 처리하는 인바운드 포트(ORDER-016).
 * 이벤트 필드는 project-service의 {@code RewardEventPublisher.RewardCreatedEvent}/
 * {@code RewardUpdatedEvent}와 동일한 계약(rewardId, projectId, isLimited, quantity)이다.
 *
 * 메시지 브로커가 아직 확정되지 않아 이 포트를 호출하는 실제 리스너 어댑터
 * (예: @KafkaListener/@RabbitListener)는 없다 — 브로커가 정해지면 infrastructure/event에
 * 어댑터만 추가해 이 포트를 호출하면 된다. 그 전까지는 이 서비스(구현체: RewardStockSyncService)를
 * 직접 호출/테스트할 수 있는 형태로 완성해둔다(CLAUDE.md "구현 순서 권장" 1번 참고).
 */
public interface RewardEventListener {

    void onRewardCreated(RewardCreatedEvent event);

    void onRewardUpdated(RewardUpdatedEvent event);

    record RewardCreatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }

    record RewardUpdatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }
}
