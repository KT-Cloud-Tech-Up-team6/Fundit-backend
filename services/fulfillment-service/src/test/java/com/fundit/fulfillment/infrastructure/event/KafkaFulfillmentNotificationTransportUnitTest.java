package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaFulfillmentNotificationTransportUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaFulfillmentNotificationTransport transport;

    private void setUp() {
        transport = new KafkaFulfillmentNotificationTransport(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 미등록_알림은_notification_raised_토픽으로_판매자에게_보낸다() {
        // given
        setUp();
        UUID sellerId = UUID.randomUUID();
        UUID projectPublicId = UUID.randomUUID();

        // when
        transport.sendStaleUpdateReminder(new StaleUpdateReminderEvent(123L), sellerId, projectPublicId, 42L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(sellerId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("fulfillment:42");
        assertThat(payload.get("memberId")).isEqualTo(sellerId);
        assertThat(payload.get("notifType")).isEqualTo("SELLER_UPDATE_DUE");
        assertThat(payload.get("relatedUrl")).isEqualTo("/seller/projects/" + projectPublicId + "/fulfillment");
    }

    @SuppressWarnings("unchecked")
    @Test
    void 일정변경_알림은_notification_raised_토픽으로_참여자에게_보낸다() {
        // given
        setUp();
        UUID memberId = UUID.randomUUID();
        UUID projectPublicId = UUID.randomUUID();
        Instant newPlannedDate = Instant.parse("2026-09-10T00:00:00Z");

        // when
        transport.sendScheduleChanged(
                new ScheduleChangedEvent(123L, FulfillmentStage.SHIPPING_OUT, ScheduleChangeReasonType.STOCK_SHORTAGE,
                        newPlannedDate),
                memberId, projectPublicId, 7L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(memberId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("fulfillment:7");
        assertThat(payload.get("notifType")).isEqualTo("SHIPPING_UPDATE");
        assertThat(payload.get("relatedUrl")).isEqualTo("/my/fundings?projectId=" + projectPublicId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 자동확정_알림은_notification_raised_토픽으로_구매자에게_보낸다() {
        // given
        setUp();
        UUID buyerId = UUID.randomUUID();
        UUID fundingPublicId = UUID.randomUUID();

        // when
        transport.sendReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(1024L), buyerId, fundingPublicId, 99L);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_RAISED), eq(buyerId.toString()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventId")).isEqualTo("fulfillment:99");
        assertThat(payload.get("notifType")).isEqualTo("SHIPPING_UPDATE");
        assertThat(payload.get("relatedUrl")).isEqualTo("/my/fundings/" + fundingPublicId + "/shipping");
    }
}
