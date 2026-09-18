package com.fundit.project.infrastructure.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpInventoryQueryClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpInventoryQueryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpInventoryQueryClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_잔여재고를_조회한다() {
        server.expect(requestTo("http://localhost:8084/api/v1/inventories/1"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {
                          "rewardId": 1,
                          "remainingStock": 37
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Integer> remainingStock = client.getRemainingStock(1L);

        assertThat(remainingStock).contains(37);
        server.verify();
    }

    @Test
    void remainingStock이_없으면_빈_값을_반환한다() {
        server.expect(requestTo("http://localhost:8084/api/v1/inventories/2"))
                .andRespond(withSuccess("""
                        {
                          "rewardId": 2
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Integer> remainingStock = client.getRemainingStock(2L);

        assertThat(remainingStock).isEmpty();
    }

    @Test
    void 호출이_실패하면_예외_대신_빈_값으로_degrade한다() {
        server.expect(requestTo("http://localhost:8084/api/v1/inventories/3"))
                .andRespond(withServerError());

        Optional<Integer> remainingStock = client.getRemainingStock(3L);

        assertThat(remainingStock).isEmpty();
    }
}
