package com.fundit.order.infrastructure.live;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.live.LiveStatusClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * live-service {@code InternalLiveController} 어댑터.
 * 세 엔드포인트가 모두 {@code InternalLiveStatusResponse} 하나를 돌려줘서 조회 로직도 하나로 둔다.
 */
@Component
public class LiveServiceLiveStatusClient implements LiveStatusClient {

    private final RestClient liveServiceRestClient;
    private final String internalApiKey;

    public LiveServiceLiveStatusClient(RestClient liveServiceRestClient,
                                       @Value("${internal-api.key}") String internalApiKey) {
        this.liveServiceRestClient = liveServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<LiveStatus> findByLiveId(UUID liveId) {
        return fetch("/internal/v1/lives/{liveId}/status", liveId);
    }

    @Override
    public Optional<LiveStatus> findActiveByProject(UUID projectId) {
        return fetch("/internal/v1/lives/by-project/{projectId}/active-status", projectId);
    }

    @Override
    public Optional<LiveStatus> findBySessionId(Long sessionId) {
        return fetch("/internal/v1/lives/sessions/{sessionId}/status", sessionId);
    }

    /**
     * 세션 없음(200+전부 null, 또는 아직 200+null로 안 바뀐 기존 엔드포인트의 404)은 {@code empty},
     * 그 외 실패는 전부 {@link DependencyFailureException}이다. 404를 실패로 묶으면
     * "조회 실패면 쿠폰 거부 / 주문은 진행"이라는 정책이 통째로 뒤집힌다.
     */
    private Optional<LiveStatus> fetch(String uriTemplate, Object id) {
        try {
            InternalLiveStatusResponse response = liveServiceRestClient.get()
                    .uri(uriTemplate, id)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalLiveStatusResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("live-service 응답 본문 없음"));
            }
            if (response.sessionId() == null) {
                return Optional.empty();
            }
            return Optional.of(new LiveStatus(response.liveId(), response.sessionId(),
                    response.status(), response.sellerId()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalLiveStatusResponse(UUID liveId, Long sessionId, String status, UUID sellerId) {
    }
}
