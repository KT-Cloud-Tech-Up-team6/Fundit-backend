package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundNotificationStatus;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaPaymentNotificationTransportUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaPaymentNotificationTransport transport;

    private void setUp() {
        transport = new KafkaPaymentNotificationTransport(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void 환불완료_상태는_완료_문구로_보낸다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();

        // when
        transport.sendRefundStatusChanged(
                new RefundStatusChangedEvent(1024L, memberId, RefundNotificationStatus.COMPLETED), 42L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(memberId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("payment:42");
        assertThat(payload.get("notifType")).isEqualTo("REFUND_STATUS");
        assertThat(payload.get("title")).isEqualTo("환불이 완료되었어요");
    }

    @SuppressWarnings("unchecked")
    @Test
    void 반려_상태는_반려_문구로_보낸다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();

        // when
        transport.sendRefundStatusChanged(
                new RefundStatusChangedEvent(1024L, memberId, RefundNotificationStatus.REJECTED), 43L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(memberId.toString()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("title")).isEqualTo("환불 신청이 반려되었어요");
    }
}
