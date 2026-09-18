package com.fundit.payment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
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
class KafkaPaymentEventTransportUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaPaymentEventTransport transport;

    private void setUp() {
        transport = new KafkaPaymentEventTransport(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void 결제완료_발행시_payment_completed_토픽으로_fundingId를_키로_보낸다() {
        // given
        setUp();
        UUID fundingId = new UUID(0L, 1024L);
        var event = new PaymentEventTransport.PaymentCompletedTransportEvent(fundingId, 7L);

        // when
        transport.sendPaymentCompleted(event, 42L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_COMPLETED), eq(fundingId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("payment:42");
        assertThat(payload.get("fundingId")).isEqualTo(fundingId);
        assertThat(payload.get("couponIssuanceId")).isEqualTo(7L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 환불완료_발행시_refund_completed_토픽으로_모든_필드를_채워_보낸다() {
        // given
        setUp();
        UUID fundingId = new UUID(0L, 2048L);
        var event = new PaymentEventTransport.RefundCompletedTransportEvent(fundingId, 9L, "CANCELLED_BY_MEMBER", true);

        // when
        transport.sendRefundCompleted(event, 77L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REFUND_COMPLETED), eq(fundingId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("payment:77");
        assertThat(payload.get("fundingId")).isEqualTo(fundingId);
        assertThat(payload.get("couponIssuanceId")).isEqualTo(9L);
        assertThat(payload.get("refundReason")).isEqualTo("CANCELLED_BY_MEMBER");
        assertThat(payload.get("fullRefund")).isEqualTo(true);
    }
}
