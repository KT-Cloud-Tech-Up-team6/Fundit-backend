package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.funding.FundingSuccessEventListener.FundingSucceededEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentTrackerInitializationServiceUnitTest {

    private static final UUID PROJECT_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000001024");

    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    private FulfillmentTrackerInitializationService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentTrackerInitializationService(trackerRepository, projectOwnershipClient);
    }

    @Test
    void 트래커가_없으면_PRODUCTION_START로_생성한다() {
        // given
        when(trackerRepository.existsByProjectId(PROJECT_PUBLIC_ID)).thenReturn(false);
        var event = new FundingSucceededEvent(1024L, 123L, PROJECT_PUBLIC_ID, ORDER_ID);

        // when
        service.onFundingSucceeded(event);

        // then
        ArgumentCaptor<FulfillmentTracker> captor = ArgumentCaptor.forClass(FulfillmentTracker.class);
        verify(trackerRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_PUBLIC_ID);
        assertThat(captor.getValue().getCurrentStage()).isEqualTo(FulfillmentStage.PRODUCTION_START);
    }

    @Test
    void 이미_트래커가_있으면_아무것도_하지_않는다() {
        // given
        when(trackerRepository.existsByProjectId(PROJECT_PUBLIC_ID)).thenReturn(true);
        var event = new FundingSucceededEvent(1024L, 123L, PROJECT_PUBLIC_ID, ORDER_ID);

        // when
        service.onFundingSucceeded(event);

        // then
        verify(trackerRepository, never()).save(any());
    }

    @Test
    void projectPublicId가_없으면_Long_projectId를_해석한다() {
        // given
        when(projectOwnershipClient.getPublicId(123L)).thenReturn(PROJECT_PUBLIC_ID);
        when(trackerRepository.existsByProjectId(PROJECT_PUBLIC_ID)).thenReturn(false);
        var event = new FundingSucceededEvent(1024L, 123L, null, null);

        // when
        service.onFundingSucceeded(event);

        // then
        ArgumentCaptor<FulfillmentTracker> captor = ArgumentCaptor.forClass(FulfillmentTracker.class);
        verify(trackerRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_PUBLIC_ID);
    }
}
