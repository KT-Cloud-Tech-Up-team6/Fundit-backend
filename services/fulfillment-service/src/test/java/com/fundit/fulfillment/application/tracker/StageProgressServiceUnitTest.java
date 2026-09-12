package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
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
class StageProgressServiceUnitTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    private StageProgressService service;

    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StageProgressService(trackerRepository, stageDetailJpaRepository, projectOwnershipClient);
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
    }

    @Test
    void 본인_소유_프로젝트면_단계를_전환한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(trackerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        FulfillmentTracker result = service.transitionStage(123L, sellerId, FulfillmentStage.SHIPPING_OUT);

        // then
        assertThat(result.getCurrentStage()).isEqualTo(FulfillmentStage.SHIPPING_OUT);
    }

    @Test
    void 상세내용을_등록하면_트래커의_마지막_갱신시각이_리셋된다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.save(any())).thenAnswer(inv -> {
            FulfillmentStageDetailJpaEntity entity = inv.getArgument(0);
            return FulfillmentStageDetailJpaEntity.builder()
                    .id(501L).trackerId(entity.getTrackerId()).stage(entity.getStage())
                    .plannedStartAt(entity.getPlannedStartAt()).plannedEndAt(entity.getPlannedEndAt())
                    .detailText(entity.getDetailText()).updatedAt(entity.getUpdatedAt())
                    .build();
        });

        // when
        FulfillmentStageDetailJpaEntity saved = service.registerStageDetail(123L, sellerId,
                FulfillmentStage.MANUFACTURING, null, null, "생산 시작");

        // then
        assertThat(saved.getId()).isEqualTo(501L);
        assertThat(saved.getDetailText()).isEqualTo("생산 시작");
        ArgumentCaptor<FulfillmentTracker> captor = ArgumentCaptor.forClass(FulfillmentTracker.class);
        verify(trackerRepository).save(captor.capture());
        assertThat(captor.getValue().getLastUpdatedAt()).isNotNull();
    }
}
