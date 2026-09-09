package com.fundit.payment.infrastructure.funding;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOrderFundingClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpOrderFundingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpOrderFundingClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_펀딩_스냅샷을_조회한다() {
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {
                          "memberId": "%s",
                          "sellerId": "%s",
                          "status": "PENDING",
                          "finalAmount": 89000,
                          "orderName": "테스트 주문",
                          "couponIssuanceId": 7
                        }
                        """.formatted(memberId, sellerId), MediaType.APPLICATION_JSON));

        var snapshot = client.fetch(1024L);

        assertThat(snapshot.memberId()).isEqualTo(memberId);
        assertThat(snapshot.sellerId()).isEqualTo(sellerId);
        assertThat(snapshot.status()).isEqualTo("PENDING");
        assertThat(snapshot.finalAmount()).isEqualTo(89_000L);
        assertThat(snapshot.orderName()).isEqualTo("테스트 주문");
        assertThat(snapshot.couponIssuanceId()).isEqualTo(7L);
        server.verify();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch(1024L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
