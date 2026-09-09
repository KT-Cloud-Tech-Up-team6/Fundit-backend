package com.fundit.payment.infrastructure.funding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubOrderFundingClientUnitTest {

    private final StubOrderFundingClient client = new StubOrderFundingClient();

    @Test
    void 같은_fundingId면_항상_같은_스냅샷을_반환한다() {
        var first = client.fetch(1024L);
        var second = client.fetch(1024L);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.finalAmount()).isEqualTo(10_000L);
        assertThat(first.orderName()).contains("1024");
        assertThat(first.couponIssuanceId()).isNull();
    }
}
