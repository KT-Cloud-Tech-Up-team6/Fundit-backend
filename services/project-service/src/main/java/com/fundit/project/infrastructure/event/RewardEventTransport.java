package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;

/**
 * 아웃박스에 적재된 리워드 수량 이벤트를 실제 채널로 보내는 전송 포트.
 * 브로커(RabbitMQ/Kafka)가 확정되면 이 인터페이스의 구현체만 교체한다.
 */
public interface RewardEventTransport {

    void sendCreated(RewardCreatedEvent event);

    void sendUpdated(RewardUpdatedEvent event);
}
