package com.fundit.fulfillment.infrastructure.funding;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubOrderFundingClientUnitTest {

    private final StubOrderFundingClient client = new StubOrderFundingClient();

    @Test
    void 같은_fundingId로_호출하면_같은_값을_반환한다() {
        var first = client.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"));
        var second = client.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"));

        assertThat(first).isEqualTo(second);
        assertThat(first.fundingPublicId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001024"));
    }
}
