package com.fundit.payment.infrastructure.event;

/**
 * 아웃박스에 적재된 결제/환불 이벤트를 실제 채널(브로커)로 보내는 전송 포트.
 * 필드 구성은 order-service가 이미 구현해둔 소비자 코드({@code PaymentEventListener})의
 * payload 계약과 정확히 일치해야 한다(payment-service CLAUDE.md "⚠️ 가장 먼저 읽을 것").
 */
public interface PaymentEventTransport {

    void sendPaymentCompleted(PaymentCompletedTransportEvent event);

    void sendRefundCompleted(RefundCompletedTransportEvent event);

    record PaymentCompletedTransportEvent(Long fundingId, Long couponIssuanceId) {
    }

    record RefundCompletedTransportEvent(Long fundingId, Long couponIssuanceId, String refundReason,
                                          boolean fullRefund) {
    }
}
