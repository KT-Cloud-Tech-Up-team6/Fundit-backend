package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 결제/환불 완료와 같은 트랜잭션에서 아웃박스에만 적재한다(PAYMENT-002/004/005/007/008/017
 * "절대 하지 말아야 할 것" — 별도 트랜잭션으로 분리 금지). 실제 발행은
 * {@link PaymentEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxPaymentEventPublisher implements PaymentEventPublisher {

    private final PaymentEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("couponIssuanceId", event.couponIssuanceId());
        payload.put("paidAt", event.paidAt() == null ? null : event.paidAt().toString());
        outboxRepository.save(PaymentEventOutboxJpaEntity.builder()
                .eventType(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED)
                .paymentId(event.paymentId())
                .fundingId(event.fundingId())
                .payload(payload)
                .build());
    }

    @Override
    public void publishRefundCompleted(RefundCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("couponIssuanceId", event.couponIssuanceId());
        payload.put("refundReason", event.refundReason().name());
        payload.put("fullRefund", event.fullRefund());
        outboxRepository.save(PaymentEventOutboxJpaEntity.builder()
                .eventType(PaymentEventOutboxJpaEntity.TYPE_REFUND_COMPLETED)
                .paymentId(event.paymentId())
                .fundingId(event.fundingId())
                .payload(payload)
                .build());
    }
}
