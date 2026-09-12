package com.fundit.fulfillment.infrastructure.funding;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceClientConfigUnitTest {

    @Test
    void 타임아웃이_설정된_RestClient를_만든다() {
        RestClient client = new OrderServiceClientConfig().orderServiceRestClient("http://localhost:8084", 2000, 3000);

        assertThat(client).isNotNull();
    }
}
