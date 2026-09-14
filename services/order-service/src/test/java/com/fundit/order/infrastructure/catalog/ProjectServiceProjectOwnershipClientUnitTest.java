package com.fundit.order.infrastructure.catalog;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceProjectOwnershipClientUnitTest {

    private MockRestServiceServer server;
    private ProjectServiceProjectOwnershipClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8083");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProjectServiceProjectOwnershipClient(builder.build());
    }

    @Test
    void 판매자_id를_조회한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8083/api/v1/projects/123"))
                .andRespond(withSuccess("""
                        {"projectId": "123", "title": "프로젝트", "seller": {"sellerId": "%s", "displayName": "메이커"}}
                        """.formatted(sellerId), MediaType.APPLICATION_JSON));

        // when
        Optional<UUID> result = client.findSellerId(123L);

        // then
        assertThat(result).contains(sellerId);
        server.verify();
    }

    @Test
    void 판매자_정보가_없으면_빈값을_반환한다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/123"))
                .andRespond(withSuccess("""
                        {"projectId": "123", "title": "프로젝트", "seller": null}
                        """, MediaType.APPLICATION_JSON));

        // when
        Optional<UUID> result = client.findSellerId(123L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/123"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.findSellerId(123L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
