package com.fundit.order.infrastructure.member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoopMemberDisclosureClientUnitTest {

    private final NoopMemberDisclosureClient client = new NoopMemberDisclosureClient();

    @Test
    void member_service_연동_전까지는_항상_비공개로_취급한다() {
        assertThat(client.isPublicConsent(UUID.randomUUID())).isFalse();
    }
}
