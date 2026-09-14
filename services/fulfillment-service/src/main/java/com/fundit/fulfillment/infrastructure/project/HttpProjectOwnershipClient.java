package com.fundit.fulfillment.infrastructure.project;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * project-service 내부 API 실제 구현체 — project-service가 {@code Long projectId} 기준
 * 내부 조회 엔드포인트(예: {@code GET /internal/projects/{projectId}})를 실제로 노출하면
 * {@code project.integration.ownership-client.mode=http}로 전환해 활성화한다. 그 엔드포인트는
 * 아직 project-service에 없다(연동 이슈로 남김) — order-service의 {@code internal/fundings}와
 * 동일한 상황이다. 인증은 {@link AuthHeaders#INTERNAL_API_KEY} 공유 시크릿을 그대로 사용한다.
 */
@Component
@ConditionalOnProperty(prefix = "project.integration.ownership-client", name = "mode", havingValue = "http")
public class HttpProjectOwnershipClient implements ProjectOwnershipClient {

    private final RestClient projectServiceRestClient;
    private final String internalApiKey;

    public HttpProjectOwnershipClient(RestClient projectServiceRestClient,
                                       @Value("${internal-api.key}") String internalApiKey) {
        this.projectServiceRestClient = projectServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public UUID getSellerId(Long projectId) {
        try {
            InternalProjectResponse response = projectServiceRestClient.get()
                    .uri("/internal/projects/{projectId}", projectId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalProjectResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("project-service 응답 본문 없음"));
            }
            return response.sellerId();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalProjectResponse(UUID sellerId) {
    }
}
