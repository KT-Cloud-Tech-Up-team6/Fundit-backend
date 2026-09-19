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

class HttpFundingParticipantsClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpFundingParticipantsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpFundingParticipantsClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_참여자_목록을_조회한다() {
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        server.expect(requestTo("http://localhost:8084/internal/projects/" + projectId + "/funding-participants"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {"memberIds": ["%s", "%s"]}
                        """.formatted(memberId1, memberId2), MediaType.APPLICATION_JSON));

        var memberIds = client.listParticipantMemberIds(projectId);

        assertThat(memberIds).containsExactly(memberId1, memberId2);
        server.verify();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        server.expect(requestTo("http://localhost:8084/internal/projects/" + projectId + "/funding-participants"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listParticipantMemberIds(projectId))
                .isInstanceOf(DependencyFailureException.class);
    }
}
