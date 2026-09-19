package com.fundit.payment.infrastructure.funding;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StubOrderFundingClientUnitTest {

    private final StubOrderFundingClient client = new StubOrderFundingClient();

    @Test
    void 같은_orderId면_항상_같은_스냅샷을_반환한다() {
        UUID orderId = UUID.randomUUID();
        var first = client.fetch(orderId);
        var second = client.fetch(orderId);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.finalAmount()).isEqualTo(10_000L);
        assertThat(first.fundingPublicId()).isEqualTo(orderId);
        assertThat(first.couponIssuanceId()).isNull();
    }

    @Test
    void 내부PK_조회는_결정적_fundingPublicId를_채운다() {
        var snapshot = client.fetchByInternalId(1024L);

        assertThat(snapshot.fundingPublicId()).isEqualTo(new UUID(2L, 1024L));
        assertThat(snapshot.memberId()).isEqualTo(new UUID(0L, 1024L));
        assertThat(snapshot.orderName()).contains("1024");
    }
}
