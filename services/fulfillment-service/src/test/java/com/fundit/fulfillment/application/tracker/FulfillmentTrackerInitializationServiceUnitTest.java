package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.funding.FundingSuccessEventListener.FundingSucceededEvent;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentTrackerInitializationServiceUnitTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;

    private FulfillmentTrackerInitializationService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentTrackerInitializationService(trackerRepository);
    }

    @Test
    void 트래커가_없으면_PRODUCTION_START로_생성한다() {
        // given
        when(trackerRepository.existsByProjectId(123L)).thenReturn(false);
        var event = new FundingSucceededEvent(1024L, 123L);

        // when
        service.onFundingSucceeded(event);

        // then
        ArgumentCaptor<FulfillmentTracker> captor = ArgumentCaptor.forClass(FulfillmentTracker.class);
        verify(trackerRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(123L);
        assertThat(captor.getValue().getCurrentStage()).isEqualTo(FulfillmentStage.PRODUCTION_START);
    }

    @Test
    void 이미_트래커가_있으면_아무것도_하지_않는다() {
        // given
        when(trackerRepository.existsByProjectId(123L)).thenReturn(true);
        var event = new FundingSucceededEvent(1024L, 123L);

        // when
        service.onFundingSucceeded(event);

        // then
        verify(trackerRepository, never()).save(any());
    }
}
