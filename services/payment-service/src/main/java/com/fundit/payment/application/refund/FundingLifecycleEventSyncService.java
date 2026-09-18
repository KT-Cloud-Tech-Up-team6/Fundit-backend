package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** PAYMENT-004/005 — 참여 취소(단순변심) 환불 실행 / 미달 자동환불 실행. */
@Service
@RequiredArgsConstructor
public class FundingLifecycleEventSyncService implements FundingLifecycleEventListener {

    private static final Logger log = LoggerFactory.getLogger(FundingLifecycleEventSyncService.class);

    private final RefundExecutionService refundExecutionService;

    @Override
    public void onFundingCancelledByMember(FundingCancelledByMemberEvent event) {
        if (event.orderId() == null) {
            log.warn("orderId가 없는 레거시 FundingCancelledByMember 이벤트는 건너뜁니다. fundingId={}", event.fundingId());
            return;
        }
        refundExecutionService.executeFullRefund(event.orderId(), RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");
    }

    @Override
    public void onFundingGoalFailed(FundingGoalFailedEvent event) {
        if (event.orderId() == null) {
            log.warn("orderId가 없는 레거시 FundingGoalFailed 이벤트는 건너뜁니다. fundingId={}", event.fundingId());
            return;
        }
        refundExecutionService.executeFullRefundOrAwaitAlternateAccount(event.orderId(),
                RefundTriggerType.GOAL_FAILED_AUTO, "목표금액 미달 자동환불");
    }
}
