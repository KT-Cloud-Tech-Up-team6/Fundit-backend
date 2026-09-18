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
 * project-service 연동 구현체. UUID(publicId) 기준은 공개 상세 API
 * ({@code GET /api/v1/projects/{projectId}})를 쓰고, 레거시 Long PK는 내부 전용 API
 * ({@code GET /internal/projects/{projectId}})를 쓴다.
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
        return fetchInternal(projectId).sellerId();
    }

    @Override
    public UUID getPublicId(Long projectId) {
        return fetchInternal(projectId).publicId();
    }

    @Override
    public UUID getSellerId(UUID projectId) {
        PublicProjectResponse response = fetchPublic(projectId);
        if (response.seller() == null || response.seller().sellerId() == null) {
            throw new DependencyFailureException(new IllegalStateException("project-service 판매자 정보 없음"));
        }
        return response.seller().sellerId();
    }

    @Override
    public UUID getPublicId(UUID projectId) {
        PublicProjectResponse response = fetchPublic(projectId);
        if (response.projectId() == null) {
            throw new DependencyFailureException(new IllegalStateException("project-service 공개 ID 없음"));
        }
        return response.projectId();
    }

    private InternalProjectResponse fetchInternal(Long projectId) {
        try {
            InternalProjectResponse response = projectServiceRestClient.get()
                    .uri("/internal/projects/{projectId}", projectId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalProjectResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("project-service 응답 본문 없음"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private PublicProjectResponse fetchPublic(UUID projectId) {
        try {
            PublicProjectResponse response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .body(PublicProjectResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("project-service 응답 본문 없음"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalProjectResponse(UUID sellerId, UUID publicId) {
    }

    private record PublicProjectResponse(UUID projectId, SellerSummary seller) {
    }

    private record SellerSummary(UUID sellerId) {
    }
}
