package com.fundit.project.infrastructure.ai.stub;

import com.fundit.project.application.ai.FundingStoryAiContracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.fundit.project.infrastructure.ai.stub.FundingStoryAiStubServerUnitTest.*;
import static org.assertj.core.api.Assertions.*;

class FundingStoryAiStubServerUnitExceptionTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://production.example.com", "http://127.0.0.1@production.example.com", "http://127.0.0.1/api"})
    void 로컬_BE가_아니면_기동을_거부한다(String backend) {
        // given / when / then
        assertThatIllegalArgumentException().isThrownBy(() -> new FundingStoryAiStubServer(
                0, URI.create(backend), "qa-token", "qa-key", "succeeded", 0));
    }

    @Test
    void 잘못된_토큰과_타_프로젝트_세션은_거부한다() throws Exception {
        // given
        try (var stub = new FundingStoryAiStubServer(0, URI.create("http://127.0.0.1:8083"),
                "qa-token", "qa-key", "no_callback", 0); var http = HttpClient.newHttpClient()) {
            stub.start();
            // when
            var request = HttpRequest.newBuilder(URI.create(stub.baseUrl() + "/api/v1/ai/sessions/latest"))
                    .header("Authorization", "Bearer wrong").header("X-Project-Id", UUID.randomUUID().toString()).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var client = client(stub);
            var session = client.createSession(UUID.randomUUID(), context());
            // then
            assertThat(response.statusCode()).isEqualTo(401);
            assertThatThrownBy(() -> client.getSession(UUID.randomUUID(), session.session_id()))
                    .isInstanceOf(com.fundit.common.error.BusinessException.class);
        }
    }

    @Test
    void 확인되지_않은_revision으로_생성을_요청하면_거부한다() throws Exception {
        // given
        try (var stub = new FundingStoryAiStubServer(0, URI.create("http://127.0.0.1:8083"),
                "qa-token", "qa-key", "no_callback", 0)) {
            stub.start();
            var client = client(stub);
            UUID project = UUID.randomUUID();
            var session = client.createSession(project, context());
            // when / then
            assertThatThrownBy(() -> client.createRun(project,
                    new RunCreateRequest(session.session_id(), session.revision(), "run", context())))
                    .isInstanceOf(com.fundit.common.error.BusinessException.class);
        }
    }

    @Test
    void 콜백_미전송_시나리오는_접수만_한다() throws Exception {
        // given
        try (var backend = new StubBackendFixture(200);
             var stub = new FundingStoryAiStubServer(0, URI.create(backend.base()), "qa-token", "qa-key", "no_callback", 0)) {
            stub.start();
            var client = client(stub);
            var request = prepare(client, backend.projectId);
            // when
            assertThat(client.createRun(backend.projectId, request).status()).isEqualTo("queued");
            // then
            assertThatThrownBy(() -> backend.completed.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(backend.callbacks).isEmpty();
            assertThat(backend.uploads).isEmpty();
        }
    }

    @Test
    void 콜백이_409이면_다른_실패_본문을_보내지_않는다() throws Exception {
        // given
        try (var backend = new StubBackendFixture(409);
             var stub = new FundingStoryAiStubServer(0, URI.create(backend.base()), "qa-token", "qa-key", "failed", 0)) {
            stub.start();
            var client = client(stub);
            var request = prepare(client, backend.projectId);
            // when
            var accepted = client.createRun(backend.projectId, request);
            // then
            assertThatThrownBy(() -> stub.delivery(accepted.run_id()).get(5, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(backend.callbacks).hasSize(1);
        }
    }
}
