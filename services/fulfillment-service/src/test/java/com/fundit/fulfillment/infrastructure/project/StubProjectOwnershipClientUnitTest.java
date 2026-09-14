package com.fundit.fulfillment.infrastructure.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubProjectOwnershipClientUnitTest {

    private final StubProjectOwnershipClient client = new StubProjectOwnershipClient();

    @Test
    void 같은_projectId로_호출하면_같은_값을_반환한다() {
        var first = client.getSellerId(123L);
        var second = client.getSellerId(123L);

        assertThat(first).isEqualTo(second);
    }
}
