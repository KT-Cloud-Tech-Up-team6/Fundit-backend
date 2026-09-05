package com.fundit.project.infrastructure.event;

import com.fundit.project.application.reward.RewardEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 메시지 브로커가 아직 없어 실제 발행 대신 로깅만 하는 임시 구현체
 * ({@link RewardEventPublisher} 클래스 주석 참고). order-service/브로커가 준비되면 교체할 것.
 */
@Component
public class LoggingRewardEventPublisher implements RewardEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingRewardEventPublisher.class);

    @Override
    public void publishRewardCreated(RewardCreatedEvent event) {
        log.info("[placeholder] RewardCreated event (미발행, 브로커 미구성): {}", event);
    }

    @Override
    public void publishRewardUpdated(RewardUpdatedEvent event) {
        log.info("[placeholder] RewardUpdated event (미발행, 브로커 미구성): {}", event);
    }
}
