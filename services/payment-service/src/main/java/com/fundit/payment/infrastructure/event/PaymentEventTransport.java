package com.fundit.payment.infrastructure.event;

import java.util.List;
import java.util.UUID;

/**
 * 아웃박스에 적재된 결제/환불 이벤트를 실제 채널(브로커)로 보내는 전송 포트.
 * 필드 구성은 order-service가 이미 구현해둔 소비자 코드({@code PaymentEventListener})의
 * payload 계약과 정확히 일치해야 한다(payment-service CLAUDE.md "⚠️ 가장 먼저 읽을 것").
 */
public interface PaymentEventTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("payment:{outboxId}")의 재료다(event-convention.md 5번). */
    void sendPaymentCompleted(PaymentCompletedTransportEvent event, Long outboxId);

    void sendRefundCompleted(RefundCompletedTransportEvent event, Long outboxId);

    record PaymentCompletedTransportEvent(UUID fundingId, List<Long> couponIssuanceIds) {
    }

    record RefundCompletedTransportEvent(UUID fundingId, List<Long> couponIssuanceIds, String refundReason,
                                          boolean fullRefund) {
    }
}
