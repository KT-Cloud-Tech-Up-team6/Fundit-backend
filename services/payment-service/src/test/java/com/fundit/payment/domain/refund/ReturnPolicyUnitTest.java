package com.fundit.payment.domain.refund;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnPolicyUnitTest {

    private static final Instant DELIVERED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void 수령_후_7일까지는_신청_가능하고_그_이후는_불가다() {
        assertThat(ReturnPolicy.isWithinRequestWindow(DELIVERED_AT, DELIVERED_AT)).isTrue();
        assertThat(ReturnPolicy.isWithinRequestWindow(DELIVERED_AT, DELIVERED_AT.plus(Duration.ofDays(7)))).isTrue();
        assertThat(ReturnPolicy.isWithinRequestWindow(DELIVERED_AT,
                DELIVERED_AT.plus(Duration.ofDays(7)).plusSeconds(1))).isFalse();
    }

    @Test
    void 반품비를_뺀_환불액을_계산한다() {
        assertThat(ReturnPolicy.refundAmountAfterReturnFee(23_000L)).isEqualTo(18_000L);
    }

    @Test
    void 결제액이_반품비_이하면_반품비를_부담할_수_없다() {
        assertThat(ReturnPolicy.coversReturnFee(5_001L)).isTrue();
        assertThat(ReturnPolicy.coversReturnFee(5_000L)).isFalse();
        assertThat(ReturnPolicy.coversReturnFee(0L)).isFalse();
    }
}
