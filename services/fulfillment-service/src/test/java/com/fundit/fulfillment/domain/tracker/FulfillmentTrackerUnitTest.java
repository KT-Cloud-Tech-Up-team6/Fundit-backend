package com.fundit.fulfillment.domain.tracker;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentTrackerUnitTest {

    @Test
    void 생성하면_PRODUCTION_START_단계로_시작한다() {
        // when
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        // then
        assertThat(tracker.getProjectId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.PRODUCTION_START);
    }

    @Test
    void 다음_단계로_전환하면_정상_반영된다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        // when
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);

        // then
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.SHIPPING_OUT);
    }

    @Test
    void 같은_단계로_요청하면_idempotent하게_유지된다() {
        // given
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        tracker.advanceTo(FulfillmentStage.MANUFACTURING);

        // when
        tracker.advanceTo(FulfillmentStage.MANUFACTURING);

        // then
        assertThat(tracker.getCurrentStage()).isEqualTo(FulfillmentStage.MANUFACTURING);
    }
}
