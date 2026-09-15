package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.inventory.RewardEventListener;
import com.fundit.order.application.inventory.RewardEventListener.RewardCreatedEvent;
import com.fundit.order.application.inventory.RewardEventListener.RewardUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ORDER-016 — project-service가 발행하는 리워드 이벤트를 구독해 {@link RewardEventListener}
 * (={@code RewardStockSyncService})로 위임하는 얇은 어댑터. 비즈니스 로직은 여기 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RewardEventKafkaListener {

    private final RewardEventListener listener;

    @KafkaListener(topics = KafkaTopics.REWARD_CREATED, groupId = "order-service")
    public void onRewardCreated(RewardCreatedEvent event) {
        listener.onRewardCreated(event);
    }

    @KafkaListener(topics = KafkaTopics.REWARD_UPDATED, groupId = "order-service")
    public void onRewardUpdated(RewardUpdatedEvent event) {
        listener.onRewardUpdated(event);
    }
}
