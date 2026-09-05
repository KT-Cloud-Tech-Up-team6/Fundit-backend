package com.fundit.project.application.reward;

/**
 * 리워드 수량 변경을 order-service 재고 원장(inventories)에 최종적 일관성으로 동기화하기 위한
 * 아웃바운드 포트(project-service CLAUDE.md 핵심 설계 결정, ORDER-012 연동).
 *
 * 이 슬라이스 시점엔 레포 전체에 메시지 브로커(RabbitMQ/Kafka)가 아직 구성되지 않았고
 * order-service도 스캐폴딩 전이라, 실제 발행 대신 로깅만 하는 {@code LoggingRewardEventPublisher}를
 * 기본 구현체로 둔다. 브로커/큐 이름 등은 이 슬라이스에서 임의로 정하지 않고, 인프라가 준비되면
 * 이 인터페이스의 실제 구현체(예: RabbitMQ 어댑터)로 교체한다.
 */
public interface RewardEventPublisher {

    void publishRewardCreated(RewardCreatedEvent event);

    void publishRewardUpdated(RewardUpdatedEvent event);

    record RewardCreatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }

    record RewardUpdatedEvent(Long rewardId, Long projectId, boolean isLimited, Integer quantity) {
    }
}
