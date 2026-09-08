package com.fundit.project.application.reward;

/**
 * 리워드 수량 변경을 order-service 재고 원장(inventories)에 최종적 일관성으로 동기화하기 위한
 * 아웃바운드 포트(project-service CLAUDE.md 핵심 설계 결정, ORDER-012 연동).
 *
 * 호출부는 같은 트랜잭션에서 아웃박스에 적재한다({@code OutboxRewardEventPublisher}).
 * 실제 채널 발행은 워커가 재시도하고, 브로커가 확정되면 {@code RewardEventTransport} 구현체만 교체한다.
 */
public interface RewardEventPublisher {

    void publishRewardCreated(RewardCreatedEvent event);

    void publishRewardUpdated(RewardUpdatedEvent event);

    record RewardCreatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }

    record RewardUpdatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }
}
