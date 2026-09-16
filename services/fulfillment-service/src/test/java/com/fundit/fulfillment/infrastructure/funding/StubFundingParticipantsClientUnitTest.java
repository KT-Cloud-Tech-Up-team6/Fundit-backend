package com.fundit.fulfillment.infrastructure.funding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubFundingParticipantsClientUnitTest {

    private final StubFundingParticipantsClient client = new StubFundingParticipantsClient();

    @Test
    void 같은_projectId로_호출하면_같은_값을_반환한다() {
        var first = client.listParticipantMemberIds(123L);
        var second = client.listParticipantMemberIds(123L);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEmpty();
    }
}
