package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 환불 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link NotificationOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxPaymentNotificationPublisher implements PaymentNotificationPublisher {

    private final NotificationOutboxJpaRepository outboxRepository;

    @Override
    public void publishRefundStatusChanged(RefundStatusChangedEvent event) {
        outboxRepository.save(NotificationOutboxJpaEntity.builder()
                .memberId(event.memberId())
                .fundingId(event.fundingId())
                .status(event.status().name())
                .build());
    }
}
