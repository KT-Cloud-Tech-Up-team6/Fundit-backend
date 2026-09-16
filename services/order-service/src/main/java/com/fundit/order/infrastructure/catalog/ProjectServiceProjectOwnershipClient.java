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
 * project-service의 공개 상세 API({@code GET /api/v1/projects/{projectId}})는 projectId를
 * publicId(UUID)로만 받는데, order-service의 {@code fundings.project_id}는 BIGINT(내부 id)라
 * 그 경로로는 조회할 수 없다(과거엔 "[가정]"으로 남겨뒀던 부분 — 실제로 항상 404/400이 남을
 * 확인함). project-service가 새로 노출한 내부 전용 API({@code GET /internal/projects/{projectId}},
 * Long id 기준)를 대신 호출한다.
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
        try {
            InternalProjectResponse response = projectServiceRestClient.get()
                    .uri("/internal/projects/{projectId}", projectId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalProjectResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.sellerId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalProjectResponse(UUID sellerId, UUID publicId) {
    }
}
