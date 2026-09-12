package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.funding.FundingSuccessEventListener.FundingSucceededEvent;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentTrackerInitializationServiceUnitExceptionTest {

    @Mock
    private FulfillmentTrackerRepository trackerRepository;

    private FulfillmentTrackerInitializationService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentTrackerInitializationService(trackerRepository);
    }

    @Test
    void 존재확인_이후_동시_중복_이벤트로_유니크_제약_위반이_나도_무시한다() {
        // given — existsByProjectId 시점엔 없었지만 save 시점엔 경쟁 이벤트가 먼저 삽입한 경우
        when(trackerRepository.existsByProjectId(123L)).thenReturn(false);
        when(trackerRepository.save(any())).thenThrow(new DataIntegrityViolationException("uq_fulfillment_trackers_project"));
        var event = new FundingSucceededEvent(1024L, 123L);

        // when & then
        assertThatCode(() -> service.onFundingSucceeded(event)).doesNotThrowAnyException();
    }
}
