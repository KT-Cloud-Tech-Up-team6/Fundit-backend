package com.fundit.project.infrastructure.ai;

import com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFundingStoryAiClientUnitTest {

    static final String BASE_URL = "http://localhost:8000";
    static final String PROJECT_HEADER = "X-Project-Id";
    static final String TOKEN = "Bearer test-token";

    MockRestServiceServer server;
    HttpFundingStoryAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpFundingStoryAiClient(builder.build(), BASE_URL, "test-token", 1000, 1000);
    }

    @Test
    void 모든_동기_AI_호출은_api_v1_ai와_프로젝트_헤더를_사용한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        FundingStoryContext context = new FundingStoryContext(
                new ProjectFact("SOLE", new CategoryFact("테크", "가전"), "프로젝트", 1_000_000L),
                List.of(), List.of());
        String sessionJson = "{\"session_id\":\"%s\",\"revision\":1,\"messages\":[],\"missing\":[]}".formatted(sessionId);

        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions"))
                .andExpect(method(POST)).andExpect(header("Authorization", TOKEN))
                .andExpect(header(PROJECT_HEADER, projectId.toString()))
                .andRespond(withSuccess(sessionJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/latest"))
                .andExpect(method(GET)).andExpect(header("Authorization", TOKEN))
                .andRespond(withSuccess("{\"session\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/" + sessionId))
                .andExpect(method(GET)).andRespond(withSuccess(sessionJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/" + sessionId + "/start"))
                .andExpect(method(POST)).andRespond(withSuccess(
                        "{\"chat_id\":\"%s\",\"status\":\"accepted\"}".formatted(chatId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/" + sessionId + "/messages"))
                .andExpect(method(POST)).andRespond(withSuccess(
                        "{\"chat_id\":\"%s\",\"status\":\"accepted\"}".formatted(chatId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/" + sessionId + "/confirm"))
                .andExpect(method(POST)).andRespond(withSuccess(
                        "{\"session_id\":\"%s\",\"confirmed_revision\":1}".formatted(sessionId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/ai/runs"))
                .andExpect(method(POST)).andRespond(withSuccess(
                        "{\"run_id\":\"%s\",\"status\":\"queued\"}".formatted(runId), MediaType.APPLICATION_JSON));

        // when & then
        assertThat(client.createSession(projectId, context).session_id()).isEqualTo(sessionId);
        assertThat(client.getLatestSession(projectId).session()).isNull();
        assertThat(client.getSession(projectId, sessionId).session_id()).isEqualTo(sessionId);
        assertThat(client.startSession(projectId, sessionId)).isEqualTo(new ChatAcceptedResponse(chatId, "accepted"));
        assertThat(client.addMessage(projectId, sessionId, new MessageRequest("m-1", 1, "답변")))
                .isEqualTo(new ChatAcceptedResponse(chatId, "accepted"));
        assertThat(client.confirmSession(projectId, sessionId, new ConfirmRequest(1)).confirmed_revision()).isEqualTo(1);
        assertThat(client.createRun(projectId, new RunCreateRequest(sessionId, 1, "run-key", context)).run_id())
                .isEqualTo(runId);
        server.verify();
    }

    @Test
    void 채팅_SSE는_스트림을_반환하고_close_callback을_호출한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        byte[] body = "data: ready\n\n".getBytes(StandardCharsets.UTF_8);
        HttpServer httpServer = sseServer(200, body);
        try {
            String baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
            HttpFundingStoryAiClient sseClient = new HttpFundingStoryAiClient(
                    RestClient.builder().baseUrl(baseUrl).build(), baseUrl, "test-token", 1000, 1000);

            // when
            ChatEventStream stream = sseClient.openChatEvents(projectId, chatId);

            // then
            assertThat(stream.body().readAllBytes()).containsExactly(body);
            stream.close();
        } finally {
            httpServer.stop(0);
        }
    }

    static HttpServer sseServer(int status, byte[] body) {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            httpServer.createContext("/api/v1/ai/chats", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE);
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            httpServer.start();
            return httpServer;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
