package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;

/**
 * 아웃박스에 적재된 펀딩 이벤트를 실제 채널로 보내는 전송 포트.
 * 브로커(RabbitMQ/Kafka)가 확정되면 이 인터페이스의 구현체만 교체한다.
 */
public interface FundingEventTransport {

    void sendGoalFailed(FundingGoalFailedEvent event);

    void sendSucceeded(FundingSucceededEvent event);

    void sendCancelledByMember(FundingCancelledByMemberEvent event);
}
