package com.fundit.payment.infrastructure.shipping;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.refund.ShippingStatusClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * fulfillment-service 내부 API(FULFILLMENT-008)와의 요청/응답 왕복을 {@link MockRestServiceServer}로
 * 검증한다 — payment-service {@code HttpOrderFundingClientUnitTest}와 동일 패턴.
 */
class HttpShippingStatusClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpShippingStatusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8087");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpShippingStatusClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_배송_상태를_조회한다() {
        Instant deliveredAt = Instant.parse("2026-09-01T00:00:00Z");
        server.expect(requestTo("http://localhost:8087/internal/fundings/1024/fulfillment-status"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {
                          "isAlreadyShipped": true,
                          "isDelayed": false,
                          "deliveredAt": "%s",
                          "receiptConfirmedAt": null
                        }
                        """.formatted(deliveredAt), MediaType.APPLICATION_JSON));

        ShippingStatusClient.ShippingStatus status = client.fetch(1024L);

        assertThat(status.isAlreadyShipped()).isTrue();
        assertThat(status.isDelayed()).isFalse();
        assertThat(status.deliveredAt()).isEqualTo(deliveredAt);
        assertThat(status.receiptConfirmedAt()).isNull();
        server.verify();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        server.expect(requestTo("http://localhost:8087/internal/fundings/1024/fulfillment-status"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch(1024L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
