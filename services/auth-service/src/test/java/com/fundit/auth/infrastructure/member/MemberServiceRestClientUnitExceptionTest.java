package com.fundit.auth.infrastructure.member;

import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MemberServiceRestClientUnitExceptionTest {

    private static final String INTERNAL_API_KEY = "test-only-internal-api-key";

    private static MemberServiceClient.CreateMemberProfileCommand command() {
        return new MemberServiceClient.CreateMemberProfileCommand(
                UUID.randomUUID(), "test@example.com", "홍길동", "응원왕", "01012345678",
                List.of("SERVICE_USE"), null);
    }

    @Test
    void 내부API키가_틀려_401을_받으면_DependencyFailureException으로_감싼다() {
        // given
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .defaultHeader("X-Internal-Api-Key", INTERNAL_API_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemberServiceRestClient client = new MemberServiceRestClient(builder.build());

        server.expect(requestTo("http://localhost:8082/api/v1/members"))
                .andRespond(withStatus(UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> client.createProfile(command()))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void MemberService가_5xx를_반환하면_DependencyFailureException으로_감싼다() {
        // given
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .defaultHeader("X-Internal-Api-Key", INTERNAL_API_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemberServiceRestClient client = new MemberServiceRestClient(builder.build());

        server.expect(requestTo("http://localhost:8082/api/v1/members"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.createProfile(command()))
                .isInstanceOf(DependencyFailureException.class);
    }
}
