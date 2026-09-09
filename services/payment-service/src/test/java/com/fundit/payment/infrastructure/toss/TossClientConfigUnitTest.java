package com.fundit.payment.infrastructure.toss;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class TossClientConfigUnitTest {

    @Test
    void 시크릿키와_타임아웃으로_RestClient를_만든다() {
        RestClient client = new TossClientConfig().tossPaymentsRestClient("test_sk", "https://api.tosspayments.com",
                1000, 2000);

        assertThat(client).isNotNull();
    }
}
