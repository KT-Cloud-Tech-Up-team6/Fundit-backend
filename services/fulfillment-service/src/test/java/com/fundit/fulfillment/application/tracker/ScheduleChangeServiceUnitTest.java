package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleChangeServiceUnitTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    @Mock
    private FulfillmentScheduleChangeJpaRepository scheduleChangeJpaRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;
    @Mock
    private FulfillmentNotificationPublisher notificationPublisher;

    private ScheduleChangeService service;

    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScheduleChangeService(trackerRepository, stageDetailJpaRepository, scheduleChangeJpaRepository,
                projectOwnershipClient, notificationPublisher);
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
    }

    @Test
    void 기존_예상일정을_스냅샷하고_새_일정으로_갱신하며_알림을_발행한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        Instant oldPlannedDate = Instant.parse("2026-09-05T00:00:00Z");
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(1L, "SHIPPING_OUT"))
                .thenReturn(Optional.of(FulfillmentStageDetailJpaEntity.builder()
                        .id(10L).trackerId(1L).stage("SHIPPING_OUT").plannedEndAt(oldPlannedDate)
                        .detailText("포장 완료").updatedAt(oldPlannedDate).build()));
        when(scheduleChangeJpaRepository.save(any())).thenAnswer(inv -> {
            FulfillmentScheduleChangeJpaEntity e = inv.getArgument(0);
            return FulfillmentScheduleChangeJpaEntity.builder()
                    .id(88L).trackerId(e.getTrackerId()).stage(e.getStage()).reasonType(e.getReasonType())
                    .reasonDetail(e.getReasonDetail()).oldPlannedDate(e.getOldPlannedDate())
                    .newPlannedDate(e.getNewPlannedDate()).changedAt(e.getChangedAt())
                    .build();
        });
        Instant newPlannedDate = Instant.parse("2026-09-10T00:00:00Z");

        // when
        FulfillmentScheduleChangeJpaEntity result = service.registerScheduleChange(123L, sellerId,
                FulfillmentStage.SHIPPING_OUT, ScheduleChangeReasonType.STOCK_SHORTAGE, "부자재 입고 지연", newPlannedDate);

        // then
        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getOldPlannedDate()).isEqualTo(oldPlannedDate);
        assertThat(result.getNewPlannedDate()).isEqualTo(newPlannedDate);

        ArgumentCaptor<FulfillmentStageDetailJpaEntity> detailCaptor =
                ArgumentCaptor.forClass(FulfillmentStageDetailJpaEntity.class);
        verify(stageDetailJpaRepository).save(detailCaptor.capture());
        assertThat(detailCaptor.getValue().getPlannedEndAt()).isEqualTo(newPlannedDate);
        assertThat(detailCaptor.getValue().getDetailText()).isEqualTo("포장 완료");

        verify(notificationPublisher).publishScheduleChanged(
                new ScheduleChangedEvent(123L, FulfillmentStage.SHIPPING_OUT, ScheduleChangeReasonType.STOCK_SHORTAGE,
                        newPlannedDate));
    }
}
