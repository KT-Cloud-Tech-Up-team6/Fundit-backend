package com.fundit.payment.infrastructure.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnconfiguredPaymentEventTransportUnitTest {

    private final UnconfiguredPaymentEventTransport transport = new UnconfiguredPaymentEventTransport();

    @Test
    void 결제완료_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendPaymentCompleted(
                new PaymentEventTransport.PaymentCompletedTransportEvent(1024L, 7L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PaymentCompleted")
                .hasMessageContaining("1024");
    }

    @Test
    void 환불완료_발행은_브로커_미구성_예외를_던진다() {
        assertThatThrownBy(() -> transport.sendRefundCompleted(
                new PaymentEventTransport.RefundCompletedTransportEvent(2048L, null, "CANCELLED_BY_MEMBER", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RefundCompleted")
                .hasMessageContaining("2048");
    }
}
