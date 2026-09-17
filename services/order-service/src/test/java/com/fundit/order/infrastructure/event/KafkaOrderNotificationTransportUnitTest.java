package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
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
class KafkaOrderNotificationTransportUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaOrderNotificationTransport transport;

    private void setUp() {
        transport = new KafkaOrderNotificationTransport(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void 재입고_알림은_notification_raised_토픽으로_보낸다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();

        // when
        transport.sendRewardRestocked(new RewardRestockedEvent(5L, memberId), 42L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(memberId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("order:42");
        assertThat(payload.get("memberId")).isEqualTo(memberId);
        assertThat(payload.get("notifType")).isEqualTo("REWARD_RESTOCK");
        assertThat(payload.get("relatedUrl")).isEqualTo("/rewards/5");
    }

    @SuppressWarnings("unchecked")
    @Test
    void 쿠폰만료임박_알림은_notification_raised_토픽으로_보낸다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();

        // when
        transport.sendCouponExpiring(new CouponExpiringEvent(7L, memberId), 77L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(memberId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("order:77");
        assertThat(payload.get("notifType")).isEqualTo("COUPON_EXPIRING");
        assertThat(payload.get("relatedUrl")).isEqualTo("/my/coupons");
    }
}
