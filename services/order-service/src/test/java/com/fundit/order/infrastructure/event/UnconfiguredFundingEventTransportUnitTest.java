package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnconfiguredFundingEventTransportUnitTest {

    private final UnconfiguredFundingEventTransport transport = new UnconfiguredFundingEventTransport();

    @Test
    void 미달_이벤트_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendGoalFailed(new FundingGoalFailedEvent(1024L, 123L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FundingGoalFailed")
                .hasMessageContaining("1024");
    }

    @Test
    void 성립_이벤트_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendSucceeded(new FundingSucceededEvent(1024L, 123L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FundingSucceeded");
    }

    @Test
    void 취소_이벤트_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendCancelledByMember(
                new FundingCancelledByMemberEvent(1024L, 123L, UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FundingCancelledByMember");
    }
}
