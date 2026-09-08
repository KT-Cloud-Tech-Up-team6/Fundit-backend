package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import org.springframework.stereotype.Component;

/**
 * 메시지 브로커가 아직 없어 발행을 완료할 수 없다. 로깅을 성공으로 취급하지 않고
 * 예외를 던져 워커가 아웃박스 행을 미발행 상태로 재시도하게 한다(CLAUDE.md에 명시된 이름).
 */
@Component
public class UnconfiguredFundingEventTransport implements FundingEventTransport {

    @Override
    public void sendGoalFailed(FundingGoalFailedEvent event) {
        throw new IllegalStateException("메시지 브로커가 아직 구성되지 않아 FundingGoalFailed를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }

    @Override
    public void sendSucceeded(FundingSucceededEvent event) {
        throw new IllegalStateException("메시지 브로커가 아직 구성되지 않아 FundingSucceeded를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }

    @Override
    public void sendCancelledByMember(FundingCancelledByMemberEvent event) {
        throw new IllegalStateException("메시지 브로커가 아직 구성되지 않아 FundingCancelledByMember를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }
}
