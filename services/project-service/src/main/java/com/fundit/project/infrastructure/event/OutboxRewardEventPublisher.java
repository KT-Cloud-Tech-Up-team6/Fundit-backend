package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.RewardEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리워드 저장과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link RewardEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxRewardEventPublisher implements RewardEventPublisher {

    private final RewardEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishRewardCreated(RewardCreatedEvent event) {
        outboxRepository.save(toEntity(RewardEventOutboxJpaEntity.TYPE_CREATED, event.rewardId(),
                event.projectId(), event.isLimited(), event.quantity()));
    }

    @Override
    public void publishRewardUpdated(RewardUpdatedEvent event) {
        outboxRepository.save(toEntity(RewardEventOutboxJpaEntity.TYPE_UPDATED, event.rewardId(),
                event.projectId(), event.isLimited(), event.quantity()));
    }

    private static RewardEventOutboxJpaEntity toEntity(String eventType, Long rewardId, Long projectId,
                                                       boolean isLimited, Integer quantity) {
        return RewardEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .rewardId(rewardId)
                .projectId(projectId)
                .isLimited(isLimited)
                .quantity(quantity)
                .build();
    }
}
