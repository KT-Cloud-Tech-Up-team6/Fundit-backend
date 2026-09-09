package com.fundit.payment.infrastructure.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubShippingStatusClientUnitTest {

    @Test
    void 항상_미발송으로_응답한다() {
        assertThat(new StubShippingStatusClient().isAlreadyShipped(1024L)).isFalse();
    }
}
