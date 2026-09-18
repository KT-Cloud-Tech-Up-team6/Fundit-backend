package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class ProjectServiceProjectOwnershipClientUnitExceptionTest {

    private static final String BASE_URL = "http://project-service";

    @Test
    void project_service_장애는_DependencyFailure로_감싼다() {
        // given — 소유권 검증은 보안에 직결돼 실패를 무시하지 않는다. 무시하면 검증 없이 통과한다.
        UUID projectId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProjectServiceProjectOwnershipClient client =
                new ProjectServiceProjectOwnershipClient(builder.build());
        server.expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.findSellerId(projectId))
                .isInstanceOf(DependencyFailureException.class);
    }
}
