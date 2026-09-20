package com.fundit.order.infrastructure.catalog;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * project-service의 프로젝트 공개 상세({@code GET /api/v1/projects/{projectId}})는 projectId를
 * publicId(UUID)로 받는다 — cross-service ID 통일(#69) 이전에는 order-service가 Long(내부 id)을
 * 그대로 넘겨 항상 실패(404)했던 지점인데, 이제 UUID를 그대로 전달하므로 정상 동작한다.
 * 실패해도 주문 생성 자체는 막지 않기 위해 예외를 던지지 않고 빈 값으로 degrade한다.
 */
@Component
public class ProjectServiceProjectSummaryClient implements ProjectSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceProjectSummaryClient.class);

    private final RestClient projectServiceRestClient;
    private final String internalApiKey;

    public ProjectServiceProjectSummaryClient(RestClient projectServiceRestClient,
                                               @Value("${internal-api.key}") String internalApiKey) {
        this.projectServiceRestClient = projectServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<String> getProjectTitle(UUID projectId) {
        try {
            ProjectDetailResponse response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .body(ProjectDetailResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.title());
        } catch (RestClientException e) {
            log.warn("project-service 프로젝트 제목 조회 실패(스냅샷 없이 진행). projectId={}", projectId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getCategoryMajor(UUID projectId) {
        try {
            ProjectDetailResponse response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .body(ProjectDetailResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.categoryMajor());
        } catch (RestClientException e) {
            log.warn("project-service 카테고리 조회 실패(CATEGORY 쿠폰 미적용 처리). projectId={}", projectId, e);
            return Optional.empty();
        }
    }

    /** 목록 화면 창작자명/썸네일은 부가 정보라 조회 실패해도 목록 자체는 내려가야 해서 예외를 던지지 않는다. */
    @Override
    public Map<UUID, ProjectSummary> getSummaries(List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<InternalProjectSummaryResponse> response = projectServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/projects/summaries")
                            .queryParam("ids", projectIds).build())
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalProjectSummaryResponse>>() {
                    });
            if (response == null) {
                return Map.of();
            }
            return response.stream().collect(Collectors.toMap(InternalProjectSummaryResponse::projectId,
                    r -> new ProjectSummary(r.title(), r.thumbnailUrl(), r.sellerDisplayName())));
        } catch (RestClientException e) {
            log.warn("project-service 배치 요약 조회 실패(창작자명/썸네일 없이 진행)", e);
            return Map.of();
        }
    }

    private record ProjectDetailResponse(UUID projectId, String title, String status, String categoryMajor) {
    }

    private record InternalProjectSummaryResponse(UUID projectId, String title, String thumbnailUrl,
                                                    String sellerDisplayName) {
    }
}
