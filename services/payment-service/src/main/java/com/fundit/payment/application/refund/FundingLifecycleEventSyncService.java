package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** PAYMENT-004/005 — 참여 취소(단순변심) 환불 실행 / 미달 자동환불 실행. */
@Service
@RequiredArgsConstructor
public class FundingLifecycleEventSyncService implements FundingLifecycleEventListener {

    private final RefundExecutionService refundExecutionService;

    @Override
    public void onFundingCancelledByMember(FundingCancelledByMemberEvent event) {
        refundExecutionService.executeFullRefund(event.fundingId(), RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");
    }

    @Override
    public void onFundingGoalFailed(FundingGoalFailedEvent event) {
        refundExecutionService.executeFullRefundOrAwaitAlternateAccount(event.fundingId(),
                RefundTriggerType.GOAL_FAILED_AUTO, "목표금액 미달 자동환불");
    }
}
