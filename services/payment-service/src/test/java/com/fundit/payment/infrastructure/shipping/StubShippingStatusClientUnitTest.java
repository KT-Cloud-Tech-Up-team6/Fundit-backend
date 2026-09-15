package com.fundit.payment.infrastructure.shipping;

import com.fundit.payment.application.refund.ShippingStatusClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubShippingStatusClientUnitTest {

    @Test
    void 항상_미발송으로_응답한다() {
        ShippingStatusClient.ShippingStatus status = new StubShippingStatusClient().fetch(1024L);
        assertThat(status.isAlreadyShipped()).isFalse();
        assertThat(status.isDelayed()).isFalse();
        assertThat(status.deliveredAt()).isNull();
        assertThat(status.receiptConfirmedAt()).isNull();
    }
}
