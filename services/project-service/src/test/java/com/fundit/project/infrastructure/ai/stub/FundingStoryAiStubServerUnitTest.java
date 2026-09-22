package com.fundit.project.infrastructure.ai.stub;

import com.fundit.project.application.ai.FundingStoryAiContracts.*;
import com.fundit.project.infrastructure.ai.HttpFundingStoryAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FundingStoryAiStubServerUnitTest {
    static FundingStoryContext context() {
        return new FundingStoryContext(new ProjectFact("SOLE", new CategoryFact("테크", "가전"), "QA 제품", 1_000_000L),
                List.of(new RewardFact(1L, "기본", "본품", 10_000L, false, null, false, List.of())), List.of());
    }

    static HttpFundingStoryAiClient client(FundingStoryAiStubServer stub) {
        return new HttpFundingStoryAiClient(RestClient.builder().baseUrl(stub.baseUrl()).build(),
                stub.baseUrl(), "qa-token", 1000, 3000);
    }

    static RunCreateRequest prepare(HttpFundingStoryAiClient client, UUID project) throws Exception {
        var session = client.createSession(project, context());
        assertThat(client.getLatestSession(project).session().session_id()).isEqualTo(session.session_id());
        var start = client.startSession(project, session.session_id());
        assertThat(start.status()).isEqualTo("queued");
        assertThat(client.startSession(project, session.session_id())).isEqualTo(start);
        try (var events = client.openChatEvents(project, start.chat_id())) {
            assertThat(new String(events.body().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("event: message", "event: done", "\"status\":\"succeeded\"");
        }
        var updated = client.getSession(project, session.session_id());
        var message = new MessageRequest("message-1", updated.revision(), "일상 청소에 사용합니다.");
        var chat = client.addMessage(project, session.session_id(), message);
        assertThat(client.addMessage(project, session.session_id(), message)).isEqualTo(chat);
        try (var events = client.openChatEvents(project, chat.chat_id())) {
            assertThat(new String(events.body().readAllBytes(), StandardCharsets.UTF_8)).contains("event: done");
        }
        updated = client.getSession(project, session.session_id());
        assertThat(updated.summary()).isNotNull();
        assertThat(updated.missing()).isEmpty();
        var confirmed = client.confirmSession(project, session.session_id(), new ConfirmRequest(updated.revision()));
        return new RunCreateRequest(session.session_id(), confirmed.confirmed_revision(), "run-1", context());
    }

    @ParameterizedTest
    @ValueSource(strings = {"succeeded", "partially_succeeded", "failed"})
    void 시나리오별_PNG와_콜백이_BE_결과에_반영된다(String result) throws Exception {
        // given
        try (var backend = new StubBackendFixture(200);
             var stub = new FundingStoryAiStubServer(0, URI.create(backend.base()), "qa-token", "qa-key", result, 20)) {
            stub.start();
            var client = client(stub);
            var request = prepare(client, backend.projectId);
            // when
            var accepted = client.createRun(backend.projectId, request);
            assertThat(client.createRun(backend.projectId, request)).isEqualTo(accepted);
            var completed = backend.completed.get(10, TimeUnit.SECONDS);
            stub.delivery(accepted.run_id()).get(10, TimeUnit.SECONDS);
            // then
            assertThat(completed.status()).isEqualTo(result);
            assertThat(backend.uploads).hasSize(result.equals("failed") ? 0 : result.equals("succeeded") ? 2 : 1);
            assertThat(completed.failed_slots()).hasSize(result.equals("partially_succeeded") ? 1 : 0);
            assertThat(backend.callbacks).hasSize(1);
            if (result.equals("failed")) {
                assertThat(backend.project.getCoverImageUrl()).isNull();
                assertThat(completed.generated_body()).isNull();
            } else {
                assertThat(backend.project.getCoverImageUrl()).endsWith("/ai/hero.png");
                assertThat(backend.project.getIntroContent()).hasSize(completed.successful_images().size());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void 콜백_재시도는_최초_본문을_그대로_전송한다(int initialStatus) throws Exception {
        // given
        try (var backend = new StubBackendFixture(initialStatus);
             var stub = new FundingStoryAiStubServer(0, URI.create(backend.base()), "qa-token", "qa-key", "succeeded", 0)) {
            stub.start();
            var client = client(stub);
            var request = prepare(client, backend.projectId);
            // when
            var accepted = client.createRun(backend.projectId, request);
            backend.completed.get(10, TimeUnit.SECONDS);
            stub.delivery(accepted.run_id()).get(10, TimeUnit.SECONDS);
            // then
            assertThat(backend.callbacks).hasSize(2);
            assertThat(backend.callbacks.get(1)).containsExactly(backend.callbacks.get(0));
        }
    }
}
