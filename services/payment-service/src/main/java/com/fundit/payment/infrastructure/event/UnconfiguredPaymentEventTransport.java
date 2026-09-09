package com.fundit.payment.infrastructure.event;

import org.springframework.stereotype.Component;

/**
 * 메시지 브로커가 아직 없어 발행을 완료할 수 없다(레포 전체 공통 상황 — order-service
 * {@code UnconfiguredFundingEventTransport}와 동일). 로깅을 성공으로 취급하지 않고 예외를
 * 던져 워커가 아웃박스 행을 미발행 상태로 재시도하게 한다.
 */
@Component
public class UnconfiguredPaymentEventTransport implements PaymentEventTransport {

    @Override
    public void sendPaymentCompleted(PaymentCompletedTransportEvent event) {
        throw new IllegalStateException(
                "메시지 브로커가 아직 구성되지 않아 PaymentCompleted를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }

    @Override
    public void sendRefundCompleted(RefundCompletedTransportEvent event) {
        throw new IllegalStateException(
                "메시지 브로커가 아직 구성되지 않아 RefundCompleted를 발행하지 못했습니다. fundingId=" + event.fundingId());
    }
}
