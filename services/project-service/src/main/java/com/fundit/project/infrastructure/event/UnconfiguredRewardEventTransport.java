package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
import org.springframework.stereotype.Component;

/**
 * 메시지 브로커가 아직 없어 발행을 완료할 수 없다. 로깅을 성공으로 취급하지 않고
 * 예외를 던져 워커가 아웃박스 행을 미발행 상태로 재시도하게 한다.
 */
@Component
public class UnconfiguredRewardEventTransport implements RewardEventTransport {

    @Override
    public void sendCreated(RewardCreatedEvent event) {
        throw new IllegalStateException("메시지 브로커가 아직 구성되지 않아 RewardCreated를 발행하지 못했습니다. rewardId=" + event.rewardId());
    }

    @Override
    public void sendUpdated(RewardUpdatedEvent event) {
        throw new IllegalStateException("메시지 브로커가 아직 구성되지 않아 RewardUpdated를 발행하지 못했습니다. rewardId=" + event.rewardId());
    }
}
