package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentQueryServiceUnitExceptionTest {

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
    void 트래커가_없으면_예외가_발생한다() {
        // given
        when(trackerRepository.findByProjectId(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getProjectFulfillment(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
