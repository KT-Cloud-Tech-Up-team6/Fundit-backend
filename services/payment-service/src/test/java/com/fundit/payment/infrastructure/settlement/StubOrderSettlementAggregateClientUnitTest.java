package com.fundit.payment.infrastructure.settlement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubOrderSettlementAggregateClientUnitTest {

    private final StubOrderSettlementAggregateClient client = new StubOrderSettlementAggregateClient();

    @Test
    void 라인아이템과_쿠폰차감은_빈_값이다() {
        assertThat(client.fetchLineItems(1024L)).isEmpty();
        assertThat(client.fetchMakerCouponDeductionAmount(1024L)).isZero();
    }
}
