package com.fundit.order.infrastructure.catalog;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * cross-service ID 통일(#69) 이후: UUID(publicId) 기준 조회는 project-service의 공개 상세 API
 * ({@code GET /api/v1/projects/{projectId}})를 직접 쓴다(응답의 {@code seller.sellerId} 활용) —
 * 과거엔 이 경로가 Long id를 못 받아 항상 실패했지만, order-service가 이제 UUID를 그대로 넘기므로
 * 정상 동작한다. 레거시 Long projectId 기준 조회(이벤트 구독 경로, 백필 배치)는 여전히
 * project-service의 내부 전용 API({@code GET /internal/projects/{projectId}})를 쓴다.
 */
@Component
public class ProjectServiceProjectOwnershipClient implements ProjectOwnershipClient {

    private final RestClient projectServiceRestClient;
    private final String internalApiKey;

    public ProjectServiceProjectOwnershipClient(RestClient projectServiceRestClient,
                                                 @Value("${internal-api.key}") String internalApiKey) {
        this.projectServiceRestClient = projectServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<UUID> findSellerId(Long projectId) {
        return findInternalProjectResponse(projectId).map(InternalProjectResponse::sellerId);
    }

    @Override
    public Optional<UUID> findSellerId(UUID projectId) {
        try {
            PublicProjectResponse response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .body(PublicProjectResponse.class);
            return response == null || response.seller() == null
                    ? Optional.empty() : Optional.ofNullable(response.seller().sellerId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public Optional<UUID> findPublicId(Long projectId) {
        return findInternalProjectResponse(projectId).map(InternalProjectResponse::publicId);
    }

    private Optional<InternalProjectResponse> findInternalProjectResponse(Long projectId) {
        try {
            InternalProjectResponse response = projectServiceRestClient.get()
                    .uri("/internal/projects/{projectId}", projectId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalProjectResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalProjectResponse(UUID sellerId, UUID publicId) {
    }

    private record PublicProjectResponse(SellerSummary seller) {
    }

    private record SellerSummary(UUID sellerId) {
    }
}
