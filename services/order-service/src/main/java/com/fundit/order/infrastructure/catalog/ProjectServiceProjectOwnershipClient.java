package com.fundit.order.infrastructure.catalog;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/** ProjectServiceProjectSummaryClient와 동일한 projectId 식별자 형태 가정을 공유한다. */
@Component
@RequiredArgsConstructor
public class ProjectServiceProjectOwnershipClient implements ProjectOwnershipClient {

    private final RestClient projectServiceRestClient;

    @Override
    public Optional<UUID> findSellerId(Long projectId) {
        try {
            ProjectDetailResponse response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .body(ProjectDetailResponse.class);
            return response == null || response.seller() == null
                    ? Optional.empty() : Optional.ofNullable(response.seller().sellerId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record ProjectDetailResponse(String projectId, String title, SellerResponse seller) {
    }

    private record SellerResponse(UUID sellerId, String displayName) {
    }
}
