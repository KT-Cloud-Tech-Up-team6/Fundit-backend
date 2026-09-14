package com.fundit.fulfillment.infrastructure.funding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubOrderFundingClientUnitTest {

    private final StubOrderFundingClient client = new StubOrderFundingClient();

    @Test
    void 같은_fundingId로_호출하면_같은_값을_반환한다() {
        var first = client.fetch(1024L);
        var second = client.fetch(1024L);

        assertThat(first).isEqualTo(second);
        assertThat(first.projectId()).isEqualTo(1024L);
    }
}
