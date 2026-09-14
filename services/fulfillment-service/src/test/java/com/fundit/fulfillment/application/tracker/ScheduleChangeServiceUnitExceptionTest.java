package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScheduleChangeServiceUnitExceptionTest {

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
    void OTHER_사유인데_상세사유가_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> service.registerScheduleChange(123L, sellerId, FulfillmentStage.SHIPPING_OUT,
                ScheduleChangeReasonType.OTHER, null, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
        verifyNoInteractions(scheduleChangeJpaRepository, notificationPublisher);
    }

    @Test
    void 본인_소유가_아니면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> service.registerScheduleChange(123L, UUID.randomUUID(), FulfillmentStage.SHIPPING_OUT,
                ScheduleChangeReasonType.STOCK_SHORTAGE, null, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
