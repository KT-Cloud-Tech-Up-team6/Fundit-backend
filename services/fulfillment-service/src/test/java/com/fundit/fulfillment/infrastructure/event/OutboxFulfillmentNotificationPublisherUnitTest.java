package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.funding.FundingParticipantsClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxFulfillmentNotificationPublisherUnitTest {

    @Mock
    private FulfillmentEventOutboxJpaRepository outboxRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private FundingParticipantsClient fundingParticipantsClient;

    private OutboxFulfillmentNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxFulfillmentNotificationPublisher(
                outboxRepository, projectOwnershipClient, orderFundingClient, fundingParticipantsClient);
    }

    @Test
    void 미등록_알림을_판매자_1명에게_아웃박스로_적재한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectPublicId = UUID.randomUUID();
        when(projectOwnershipClient.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(sellerId);
        when(projectOwnershipClient.getPublicId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(projectPublicId);

        // when
        publisher.publishStaleUpdateReminder(new StaleUpdateReminderEvent(UUID.fromString("00000000-0000-0000-0000-000000000123")));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER);
        assertThat(captor.getValue().getProjectPublicId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        assertThat(captor.getValue().getMemberId()).isEqualTo(sellerId);
        assertThat(captor.getValue().getRelatedPublicId()).isEqualTo(projectPublicId);
    }

    @Test
    void 일정변경_알림을_참여자_수만큼_아웃박스에_적재한다() {
        // given
        Instant newPlannedDate = Instant.parse("2026-09-10T00:00:00Z");
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        UUID projectPublicId = UUID.randomUUID();
        when(projectOwnershipClient.getPublicId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(projectPublicId);
        when(fundingParticipantsClient.listParticipantMemberIds(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(List.of(memberId1, memberId2));

        // when
        publisher.publishScheduleChanged(new ScheduleChangedEvent(UUID.fromString("00000000-0000-0000-0000-000000000123"), FulfillmentStage.SHIPPING_OUT,
                ScheduleChangeReasonType.STOCK_SHORTAGE, newPlannedDate));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        List<FulfillmentEventOutboxJpaEntity> saved = captor.getAllValues();
        assertThat(saved).extracting(FulfillmentEventOutboxJpaEntity::getMemberId)
                .containsExactlyInAnyOrder(memberId1, memberId2);
        for (FulfillmentEventOutboxJpaEntity entity : saved) {
            assertThat(entity.getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_SCHEDULE_CHANGED);
            assertThat(entity.getStage()).isEqualTo("SHIPPING_OUT");
            assertThat(entity.getReasonType()).isEqualTo("STOCK_SHORTAGE");
            assertThat(entity.getNewPlannedDate()).isEqualTo(newPlannedDate);
            assertThat(entity.getRelatedPublicId()).isEqualTo(projectPublicId);
        }
    }

    @Test
    void 자동확정_알림을_구매자_1명에게_아웃박스로_적재한다() {
        // given
        UUID buyerId = UUID.randomUUID();
        UUID fundingPublicId = UUID.randomUUID();
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000007"), buyerId, fundingPublicId));

        // when
        publisher.publishReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(UUID.fromString("00000000-0000-0000-0000-000000001024")));

        // then
        ArgumentCaptor<FulfillmentEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(FulfillmentEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FulfillmentEventOutboxJpaEntity.TYPE_RECEIPT_AUTO_CONFIRMED);
        assertThat(captor.getValue().getFundingOrderId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001024"));
        assertThat(captor.getValue().getMemberId()).isEqualTo(buyerId);
        assertThat(captor.getValue().getRelatedPublicId()).isEqualTo(fundingPublicId);
    }
}
