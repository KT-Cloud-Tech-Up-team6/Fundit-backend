package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FundingLifecycleEventSyncServiceUnitTest {

    @Mock
    private RefundExecutionService refundExecutionService;

    private FundingLifecycleEventSyncService fundingLifecycleEventSyncService;

    @BeforeEach
    void setUp() {
        fundingLifecycleEventSyncService = new FundingLifecycleEventSyncService(refundExecutionService);
    }

    @Test
    void 참여취소_이벤트면_단순변심_전액취소를_실행한다() {
        // given
        var event = new FundingLifecycleEventListener.FundingCancelledByMemberEvent(1024L, 10L, UUID.randomUUID());

        // when
        fundingLifecycleEventSyncService.onFundingCancelledByMember(event);

        // then
        verify(refundExecutionService).executeFullRefund(1024L, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");
    }

    @Test
    void 목표미달_이벤트면_대체계좌_대기를_허용하며_전액취소를_실행한다() {
        // given
        var event = new FundingLifecycleEventListener.FundingGoalFailedEvent(2048L, 10L);

        // when
        fundingLifecycleEventSyncService.onFundingGoalFailed(event);

        // then
        verify(refundExecutionService).executeFullRefundOrAwaitAlternateAccount(2048L,
                RefundTriggerType.GOAL_FAILED_AUTO, "목표금액 미달 자동환불");
    }
}
