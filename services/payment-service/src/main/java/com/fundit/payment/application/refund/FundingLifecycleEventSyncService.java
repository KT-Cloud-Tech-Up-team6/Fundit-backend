package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundReasonTag;
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
                cancelReasonDetail(event));
    }

    /**
     * 사유가 실려 오면 발송 후 신청 3종과 같은 태그 포맷으로 저장한다 — 환불 목록(v2)이
     * {@code reasonType}/{@code reasonDetail}로 나눠 내려줘 FE가 문자열을 파싱하지 않아도 된다.
     * 사유 없이 취소한 건(구버전 클라이언트·구버전 메시지)은 기존 고정 문구를 그대로 쓴다.
     */
    private String cancelReasonDetail(FundingCancelledByMemberEvent event) {
        if (event.cancelReason() == null) {
            return "구매자 단순변심 참여 취소";
        }
        return RefundReasonTag.format(event.cancelReason(), event.cancelReasonDetail());
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
