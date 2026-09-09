package com.fundit.payment.infrastructure.event;

import com.fundit.payment.infrastructure.event.PaymentEventTransport.PaymentCompletedTransportEvent;
import com.fundit.payment.infrastructure.event.PaymentEventTransport.RefundCompletedTransportEvent;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * PAYMENT-016 — 미발행 아웃박스 행을 꺼내 {@link PaymentEventTransport}로 전달한다. 실패하면
 * published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (order-service FundingEventOutboxWorker와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "payment-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventOutboxWorker.class);

    private final PaymentEventOutboxJpaRepository outboxRepository;
    private final PaymentEventTransport transport;
    private final int batchSize;

    public PaymentEventOutboxWorker(PaymentEventOutboxJpaRepository outboxRepository,
                                     PaymentEventTransport transport,
                                     @Value("${payment-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (PaymentEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("결제 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(PaymentEventOutboxJpaEntity event) {
        Map<String, Object> payload = event.getPayload();
        switch (event.getEventType()) {
            case PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED -> transport.sendPaymentCompleted(
                    new PaymentCompletedTransportEvent(event.getFundingId(), toLong(payload.get("couponIssuanceId"))));
            case PaymentEventOutboxJpaEntity.TYPE_REFUND_COMPLETED -> transport.sendRefundCompleted(
                    new RefundCompletedTransportEvent(event.getFundingId(), toLong(payload.get("couponIssuanceId")),
                            (String) payload.get("refundReason"), Boolean.TRUE.equals(payload.get("fullRefund"))));
            default -> throw new IllegalStateException("알 수 없는 결제 이벤트 타입: " + event.getEventType());
        }
    }

    /** JSONB 역직렬화 시 숫자가 Integer/Long/Double 중 무엇으로 오든 안전하게 Long으로 변환한다. */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
