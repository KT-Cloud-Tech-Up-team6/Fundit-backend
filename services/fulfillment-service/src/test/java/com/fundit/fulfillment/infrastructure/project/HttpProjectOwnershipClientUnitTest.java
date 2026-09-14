package com.fundit.fulfillment.infrastructure.project;

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

class HttpProjectOwnershipClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpProjectOwnershipClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8083");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpProjectOwnershipClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_판매자_ID를_조회한다() {
        UUID sellerId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8083/internal/projects/123"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {"sellerId": "%s"}
                        """.formatted(sellerId), MediaType.APPLICATION_JSON));

        UUID result = client.getSellerId(123L);

        assertThat(result).isEqualTo(sellerId);
        server.verify();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        server.expect(requestTo("http://localhost:8083/internal/projects/123"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getSellerId(123L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
