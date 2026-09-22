package com.fundit.project.infrastructure.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFundingStoryAiClientUnitExceptionTest {

    private static final String BASE_URL = HttpFundingStoryAiClientUnitTest.BASE_URL;

    private MockRestServiceServer server;
    private HttpFundingStoryAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpFundingStoryAiClient(builder.build(), BASE_URL, "test-token", 1000, 1000);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 410, 422, 429, 500})
    void AI_HTTP_상태를_BE_오류계약으로_변환한다(int status) {
        // given
        UUID projectId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/latest"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        // when & then
        if (status == 500) {
            assertThatThrownBy(() -> client.getLatestSession(projectId))
                    .isInstanceOf(DependencyFailureException.class);
        } else {
            assertThatThrownBy(() -> client.getLatestSession(projectId))
                    .isInstanceOf(BusinessException.class);
        }
        server.verify();
    }

    @Test
    void 응답_본문이_비어있으면_의존성_실패로_처리한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/v1/ai/sessions/latest"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getLatestSession(UUID.randomUUID()))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 채팅_SSE의_비정상_응답은_BE_오류로_변환한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        HttpServer httpServer = HttpFundingStoryAiClientUnitTest.sseServer(409, new byte[0]);
        try {
            String baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
            HttpFundingStoryAiClient sseClient = new HttpFundingStoryAiClient(
                    RestClient.builder().baseUrl(baseUrl).build(), baseUrl, "test-token", 1000, 1000);

            // when & then
            assertThatThrownBy(() -> sseClient.openChatEvents(projectId, chatId))
                    .isInstanceOf(BusinessException.class);
        } finally {
            httpServer.stop(0);
        }
    }
}
