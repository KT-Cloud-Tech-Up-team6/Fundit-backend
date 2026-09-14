package com.fundit.fulfillment.domain.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTrackerUnitExceptionTest {

    @Test
    void 이전_단계로_역행하면_예외가_발생한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);

        // when & then
        assertThatThrownBy(() -> tracker.advanceTo(FulfillmentStage.MANUFACTURING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.INVALID_STAGE_TRANSITION));
    }
}
