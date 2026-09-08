package com.fundit.order.infrastructure.catalog;

import com.fundit.order.application.catalog.ProjectSummaryClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * [가정] project-service의 프로젝트 공개 상세(ProjectDomainApiSpec.md #27, PROJECT-020)는
 * {projectId} 경로변수로 projects.public_id(UUID)를 쓰는데, order-service의 fundings.project_id는
 * BIGINT(내부 id)다 — order-service 자신의 API 계약(OrderDomainApiSpec.md request의 projectId도
 * 정수 예시)과 DB 스키마가 전부 BIGINT를 전제해서, 이 클라이언트도 동일하게 BIGINT를 그대로
 * 넘긴다고 가정했다. 두 서비스의 프로젝트 식별자 형태(BIGINT vs UUID)가 실제로 다르면 이 호출은
 * 항상 실패(404)하므로, project-service 담당자와 식별자 매핑 방식을 반드시 확인해야 한다.
 * 실패해도 주문 생성 자체는 막지 않기 위해 예외를 던지지 않고 빈 값으로 degrade한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectServiceProjectSummaryClient implements ProjectSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceProjectSummaryClient.class);

    private final RestClient projectServiceRestClient;

    @Override
    public Optional<String> getProjectTitle(Long projectId) {
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

    private record ProjectDetailResponse(String projectId, String title, String status) {
    }
}
