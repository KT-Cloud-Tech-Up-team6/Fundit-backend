package com.fundit.fulfillment.infrastructure.funding;

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
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {"projectId": 123, "memberId": "%s"}
                        """.formatted(memberId), MediaType.APPLICATION_JSON));

        var snapshot = client.fetch(1024L);

        assertThat(snapshot.projectId()).isEqualTo(123L);
        assertThat(snapshot.memberId()).isEqualTo(memberId);
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
