package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentQueryServiceUnitTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    @Mock
    private FulfillmentScheduleChangeJpaRepository scheduleChangeJpaRepository;

    private FulfillmentQueryService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentQueryService(trackerRepository, stageDetailJpaRepository,
                scheduleChangeJpaRepository, 7);
    }

    @Test
    void 현재_단계_기준으로_각_단계의_상태를_계산한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);
        tracker.markProgressUpdated(Instant.now());
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(scheduleChangeJpaRepository.findByTrackerIdOrderByChangedAtDesc(1L)).thenReturn(List.of());

        // when
        var view = service.getProjectFulfillment(123L);

        // then
        assertThat(view.currentStage()).isEqualTo(FulfillmentStage.SHIPPING_OUT);
        assertThat(statusOf(view, FulfillmentStage.MANUFACTURING))
                .isEqualTo(FulfillmentQueryService.StageProgressStatus.COMPLETED);
        assertThat(statusOf(view, FulfillmentStage.SHIPPING_OUT))
                .isEqualTo(FulfillmentQueryService.StageProgressStatus.IN_PROGRESS);
        assertThat(statusOf(view, FulfillmentStage.DELIVERY))
                .isEqualTo(FulfillmentQueryService.StageProgressStatus.NOT_STARTED);
        assertThat(view.updateOverdue()).isFalse();
    }

    @Test
    void 마지막_갱신후_기준일이_지나면_updateOverdue가_true다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        tracker.markProgressUpdated(Instant.now().minus(8, ChronoUnit.DAYS));
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(scheduleChangeJpaRepository.findByTrackerIdOrderByChangedAtDesc(1L)).thenReturn(List.of());

        // when
        var view = service.getProjectFulfillment(123L);

        // then
        assertThat(view.updateOverdue()).isTrue();
    }

    @Test
    void DELIVERY_단계면_미갱신이어도_updateOverdue는_false다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        tracker.advanceTo(FulfillmentStage.DELIVERY);
        // last_updated_at을 갱신하지 않은 채로 둔다(null)
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(scheduleChangeJpaRepository.findByTrackerIdOrderByChangedAtDesc(1L)).thenReturn(List.of());

        // when
        var view = service.getProjectFulfillment(123L);

        // then
        assertThat(view.updateOverdue()).isFalse();
    }

    private FulfillmentQueryService.StageProgressStatus statusOf(
            FulfillmentQueryService.ProjectFulfillmentView view, FulfillmentStage stage) {
        return view.stages().stream().filter(s -> s.stage() == stage).findFirst().orElseThrow().status();
    }
}
