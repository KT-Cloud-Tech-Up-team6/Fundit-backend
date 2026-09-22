package com.fundit.project.infrastructure.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.project.application.ai.FundingStoryAiClient;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.domain.ProjectErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Component
public class HttpFundingStoryAiClient implements FundingStoryAiClient {

    private static final String PREFIX = "/api/v1/ai";
    private static final String PROJECT_HEADER = "X-Project-Id";

    private final RestClient restClient;
    private final HttpClient streamingClient;
    private final String baseUrl;
    private final String bearerToken;
    private final Duration streamTimeout;

    public HttpFundingStoryAiClient(
            RestClient fundingStoryAiRestClient,
            @Value("${funding-story.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${funding-story.ai.service-token:local-funding-story-token}") String serviceToken,
            @Value("${funding-story.ai.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${funding-story.ai.stream-timeout-ms:310000}") int streamTimeoutMs) {
        this.restClient = fundingStoryAiRestClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.bearerToken = "Bearer " + serviceToken;
        this.streamingClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.streamTimeout = Duration.ofMillis(streamTimeoutMs);
    }

    @Override
    public SessionResponse createSession(UUID projectId, FundingStoryContext context) {
        return call(() -> restClient.post().uri(PREFIX + "/sessions")
                .headers(headers -> setHeaders(headers, projectId))
                .body(new SessionCreateRequest(context))
                .retrieve().body(SessionResponse.class));
    }

    @Override
    public LatestSessionResponse getLatestSession(UUID projectId) {
        return call(() -> restClient.get().uri(PREFIX + "/sessions/latest")
                .headers(headers -> setHeaders(headers, projectId))
                .retrieve().body(LatestSessionResponse.class));
    }

    @Override
    public SessionResponse getSession(UUID projectId, UUID sessionId) {
        return call(() -> restClient.get().uri(PREFIX + "/sessions/{sessionId}", sessionId)
                .headers(headers -> setHeaders(headers, projectId))
                .retrieve().body(SessionResponse.class));
    }

    @Override
    public ChatAcceptedResponse startSession(UUID projectId, UUID sessionId) {
        return call(() -> restClient.post().uri(PREFIX + "/sessions/{sessionId}/start", sessionId)
                .headers(headers -> setHeaders(headers, projectId))
                .retrieve().body(ChatAcceptedResponse.class));
    }

    @Override
    public ChatAcceptedResponse addMessage(UUID projectId, UUID sessionId, MessageRequest request) {
        return call(() -> restClient.post().uri(PREFIX + "/sessions/{sessionId}/messages", sessionId)
                .headers(headers -> setHeaders(headers, projectId))
                .body(request)
                .retrieve().body(ChatAcceptedResponse.class));
    }

    @Override
    public ChatEventStream openChatEvents(UUID projectId, UUID chatId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + PREFIX + "/chats/" + chatId + "/events"))
                .timeout(streamTimeout)
                .header("Authorization", bearerToken)
                .header(PROJECT_HEADER, projectId.toString())
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build();
        try {
            HttpResponse<java.io.InputStream> response = streamingClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw mapped(response.statusCode());
            }
            return new ChatEventStream(response.body(), () -> closeQuietly(response.body()));
        } catch (IOException e) {
            throw new DependencyFailureException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public ConfirmResponse confirmSession(UUID projectId, UUID sessionId, ConfirmRequest request) {
        return call(() -> restClient.post().uri(PREFIX + "/sessions/{sessionId}/confirm", sessionId)
                .headers(headers -> setHeaders(headers, projectId))
                .body(request)
                .retrieve().body(ConfirmResponse.class));
    }

    @Override
    public RunAcceptedResponse createRun(UUID projectId, RunCreateRequest request) {
        return call(() -> restClient.post().uri(PREFIX + "/runs")
                .headers(headers -> setHeaders(headers, projectId))
                .body(request)
                .retrieve().body(RunAcceptedResponse.class));
    }

    private void setHeaders(org.springframework.http.HttpHeaders headers, UUID projectId) {
        headers.set("Authorization", bearerToken);
        headers.set(PROJECT_HEADER, projectId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("AI 응답 본문이 없습니다."));
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapped(e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private RuntimeException mapped(int status) {
        return switch (status) {
            case 400 -> new BusinessException(CommonErrorCode.INVALID_INPUT);
            case 401 -> new BusinessException(CommonErrorCode.UNAUTHORIZED);
            case 403 -> new BusinessException(CommonErrorCode.FORBIDDEN);
            case 404 -> new BusinessException(CommonErrorCode.NOT_FOUND);
            case 409 -> new BusinessException(CommonErrorCode.CONFLICT);
            case 410 -> new BusinessException(CommonErrorCode.RESOURCE_EXPIRED);
            case 422 -> new BusinessException(ProjectErrorCode.NOT_READY_TO_GENERATE);
            case 429 -> new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
            default -> new DependencyFailureException(
                    new IllegalStateException("Funding Story AI HTTP " + status));
        };
    }

    private static void closeQuietly(java.io.InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Client disconnect already closed the stream.
        }
    }
}
