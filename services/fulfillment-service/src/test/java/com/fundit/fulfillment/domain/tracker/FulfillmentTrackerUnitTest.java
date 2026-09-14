package com.fundit.fulfillment.domain.tracker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentTrackerUnitTest {

    @Test
    void 생성하면_PRODUCTION_START_단계로_시작한다() {
        // when
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);

        // then
        assertThat(tracker.getProjectId()).isEqualTo(123L);
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.PRODUCTION_START);
    }

    @Test
    void 다음_단계로_전환하면_정상_반영된다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);

        // when
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);

        // then
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.SHIPPING_OUT);
    }

    @Test
    void 같은_단계로_요청하면_idempotent하게_유지된다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);
        tracker.advanceTo(FulfillmentStage.MANUFACTURING);

        // when
        tracker.advanceTo(FulfillmentStage.MANUFACTURING);

        // then
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.MANUFACTURING);
    }
}
