package com.fundit.fulfillment.domain.tracker;

import java.util.UUID;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTrackerUnitExceptionTest {

    @Test
    void 이전_단계로_역행하면_예외가_발생한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        tracker.advanceTo(FulfillmentStage.MANUFACTURING);
        tracker.advanceTo(FulfillmentStage.INSPECTION);
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);

        // when & then
        assertThatThrownBy(() -> tracker.advanceTo(FulfillmentStage.MANUFACTURING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.INVALID_STAGE_TRANSITION));
    }

    @Test
    void 중간_단계를_건너뛰면_예외가_발생한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        // when & then
        assertThatThrownBy(() -> tracker.advanceTo(FulfillmentStage.SHIPPING_OUT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.INVALID_STAGE_TRANSITION));
    }

    @Test
    void 현재_단계가_아닌_기록을_등록하려_하면_예외가_발생한다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        // when & then
        assertThatThrownBy(() -> tracker.verifyCurrentStage(FulfillmentStage.MANUFACTURING))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.INVALID_STAGE_TRANSITION));
    }
}
