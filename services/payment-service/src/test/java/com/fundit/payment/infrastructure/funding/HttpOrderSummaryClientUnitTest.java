package com.fundit.payment.infrastructure.funding;

import com.fundit.payment.application.refund.OrderSummaryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOrderSummaryClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpOrderSummaryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpOrderSummaryClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_주문_요약을_배치_조회한다() {
        // given
        UUID orderId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8084/internal/orders/order-summaries?orderIds=" + orderId))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        [{"orderId": "%s", "projectTitle": "프로젝트",
                          "lineItems": [{"rewardId": 1, "rewardName": "리워드", "quantity": 2, "unitPrice": 10000,
                              "options": [{"optionValueId": 100, "optionGroupName": "색상", "optionValue": "블랙"}]}]}]
                        """.formatted(orderId), MediaType.APPLICATION_JSON));

        // when
        Map<UUID, OrderSummaryClient.OrderSummary> result = client.fetchBatch(List.of(orderId));

        // then
        assertThat(result.get(orderId).projectTitle()).isEqualTo("프로젝트");
        assertThat(result.get(orderId).lineItems()).hasSize(1);
        assertThat(result.get(orderId).lineItems().get(0).options())
                .containsExactly(new OrderSummaryClient.LineItemOption("색상", "블랙"));
        server.verify();
    }

    @Test
    void 호출이_실패해도_예외를_던지지_않고_빈맵으로_degrade한다() {
        UUID orderId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8084/internal/orders/order-summaries?orderIds=" + orderId))
                .andRespond(withServerError());

        Map<UUID, OrderSummaryClient.OrderSummary> result = client.fetchBatch(List.of(orderId));

        assertThat(result).isEmpty();
    }
}
