package com.fundit.fulfillment.infrastructure.project;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectServiceClientConfigUnitTest {

    @Test
    void 타임아웃이_설정된_RestClient를_만든다() {
        RestClient client = new ProjectServiceClientConfig().projectServiceRestClient("http://localhost:8083", 2000, 3000);

        assertThat(client).isNotNull();
    }
}
