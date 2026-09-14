package com.fundit.order.infrastructure.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceProjectSummaryClientUnitTest {

    private MockRestServiceServer server;
    private ProjectServiceProjectSummaryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8083");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProjectServiceProjectSummaryClient(builder.build());
    }

    @Test
    void 프로젝트_제목을_조회한다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/123"))
                .andRespond(withSuccess("""
                        {"projectId": "123", "title": "프로젝트 제목", "status": "IN_PROGRESS"}
                        """, MediaType.APPLICATION_JSON));

        // when
        Optional<String> result = client.getProjectTitle(123L);

        // then
        assertThat(result).contains("프로젝트 제목");
        server.verify();
    }

    @Test
    void 호출이_실패해도_예외를_던지지_않고_빈값으로_degrade한다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/123"))
                .andRespond(withServerError());

        // when
        Optional<String> result = client.getProjectTitle(123L);

        // then
        assertThat(result).isEmpty();
    }
}
