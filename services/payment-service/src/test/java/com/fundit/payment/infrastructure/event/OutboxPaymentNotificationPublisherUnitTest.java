package com.fundit.payment.infrastructure.event;

import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundNotificationStatus;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.NotificationOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPaymentNotificationPublisherUnitTest {

    @Mock
    private NotificationOutboxJpaRepository outboxRepository;

    @InjectMocks
    private OutboxPaymentNotificationPublisher publisher;

    @Test
    void 환불상태변경_알림을_아웃박스에_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        publisher.publishRefundStatusChanged(
                new RefundStatusChangedEvent(new UUID(0L, 1024L), memberId, RefundNotificationStatus.COMPLETED));

        // then
        ArgumentCaptor<NotificationOutboxJpaEntity> captor = ArgumentCaptor.forClass(NotificationOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getFundingId()).isEqualTo(new UUID(0L, 1024L));
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }
}
