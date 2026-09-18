package com.fundit.fulfillment.infrastructure.project;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubProjectOwnershipClientUnitTest {

    private final StubProjectOwnershipClient client = new StubProjectOwnershipClient();

    @Test
    void 같은_projectId로_호출하면_같은_값을_반환한다() {
        var first = client.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        var second = client.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        assertThat(first).isEqualTo(second);
    }
}
