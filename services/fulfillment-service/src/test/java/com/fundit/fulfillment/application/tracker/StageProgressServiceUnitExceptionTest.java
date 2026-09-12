package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageProgressServiceUnitExceptionTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    private StageProgressService service;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID otherAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StageProgressService(trackerRepository, stageDetailJpaRepository, projectOwnershipClient);
        lenient().when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
    }

    @Test
    void 본인_소유가_아니면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> service.transitionStage(123L, otherAccountId, FulfillmentStage.SHIPPING_OUT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 트래커가_없으면_예외가_발생한다() {
        // given
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.transitionStage(123L, sellerId, FulfillmentStage.SHIPPING_OUT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 이전_단계로_역행하면_예외가_발생한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));

        // when & then
        assertThatThrownBy(() -> service.transitionStage(123L, sellerId, FulfillmentStage.MANUFACTURING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.INVALID_STAGE_TRANSITION));
    }
}
