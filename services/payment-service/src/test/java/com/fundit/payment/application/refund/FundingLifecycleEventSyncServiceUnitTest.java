package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
        UUID orderId = new UUID(0L, 1024L);
        var event = new FundingLifecycleEventListener.FundingCancelledByMemberEvent(
                1024L, 10L, UUID.randomUUID(), orderId, null, null);

        // when
        fundingLifecycleEventSyncService.onFundingCancelledByMember(event);

        // then — 사유 없이 취소한 건(구버전 클라이언트)은 기존 고정 문구를 그대로 쓴다.
        verify(refundExecutionService).executeFullRefund(orderId, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");
    }

    @Test
    void 참여취소_사유가_실려오면_태그_포맷으로_저장한다() {
        // given
        UUID orderId = new UUID(0L, 2048L);
        var event = new FundingLifecycleEventListener.FundingCancelledByMemberEvent(
                2048L, 10L, UUID.randomUUID(), orderId, "ETC", "배송지를 잘못 입력했어요");

        // when
        fundingLifecycleEventSyncService.onFundingCancelledByMember(event);

        // then — 환불 목록(v2)이 reasonType/reasonDetail로 나눠 내려주는 포맷과 같다.
        verify(refundExecutionService).executeFullRefund(orderId, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "[ETC] 배송지를 잘못 입력했어요");
    }

    @Test
    void 목표미달_이벤트면_대체계좌_대기를_허용하며_전액취소를_실행한다() {
        // given
        UUID orderId = new UUID(0L, 2048L);
        var event = new FundingLifecycleEventListener.FundingGoalFailedEvent(2048L, 10L, orderId);

        // when
        fundingLifecycleEventSyncService.onFundingGoalFailed(event);

        // then
        verify(refundExecutionService).executeFullRefundOrAwaitAlternateAccount(orderId,
                RefundTriggerType.GOAL_FAILED_AUTO, "목표금액 미달 자동환불");
    }

    @Test
    void orderId가_없으면_레거시_메시지를_건너뛴다() {
        var event = new FundingLifecycleEventListener.FundingGoalFailedEvent(2048L, 10L, null);

        fundingLifecycleEventSyncService.onFundingGoalFailed(event);

        verifyNoInteractions(refundExecutionService);
    }
}
