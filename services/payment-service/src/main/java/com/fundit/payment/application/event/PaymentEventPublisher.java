package com.fundit.payment.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * payment-service가 발행하는 결제완료/환불완료 이벤트의 아웃바운드 포트(PAYMENT-002/004/005/007/008/017).
 * 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다({@code OutboxPaymentEventPublisher}, PAYMENT-016
 * 워커가 실제 채널로 재시도 발행). order-service의 {@code FundingEventPublisher}와 동일 패턴.
 *
 * <p><b>payload 계약은 order-service가 이미 구현해둔 소비자 코드
 * ({@code order-service PaymentEventListener}) 기준이다</b> — payment-service CLAUDE.md
 * "⚠️ 가장 먼저 읽을 것" 섹션이 PaymentApiSpec.md/PaymentFunctionalSpec.md보다 우선한다.
 * 특히 {@code couponIssuanceId}를 반드시 채워 보내야 하고, {@code refundReason}은 order-service가
 * 정의한 4종 enum 이름을 그대로 써야 한다.
 */
public interface PaymentEventPublisher {

    void publishPaymentCompleted(PaymentCompletedEvent event);

    void publishRefundCompleted(RefundCompletedEvent event);

    record PaymentCompletedEvent(UUID paymentId, Long fundingId, Long couponIssuanceId, Instant paidAt) {
    }

    /**
     * order-service {@code PaymentEventListener.RefundReason}과 이름을 반드시 일치시킨다.
     * 이 서비스의 {@code RefundTriggerType}과의 매핑은 {@code RefundReasonMapper} 참고.
     */
    enum RefundReason {
        GOAL_FAILURE_AUTO_REFUND, CANCELLED_BY_MEMBER, POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY
    }

    record RefundCompletedEvent(UUID paymentId, Long fundingId, Long couponIssuanceId,
                                 RefundReason refundReason, boolean fullRefund) {
    }
}
