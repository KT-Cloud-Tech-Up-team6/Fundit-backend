package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.project.ProjectOwnershipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * project-service의 <b>공개</b> 상세 API({@code GET /api/v1/projects/{projectId}})를 쓴다.
 * 응답의 {@code seller.sellerId}가 소유자다.
 *
 * <p>내부 API({@code /internal/projects/{projectId}})를 쓰지 않는 이유: 그쪽은 project-service의
 * 내부 PK(Long)만 받는데 live는 publicId(UUID)만 안다. UUID → 내부 id를 알려주는 경로가 없어
 * 호출 자체가 불가능하다. 공개 API는 같은 정보를 UUID로 조회할 수 있고 내부 키도 필요 없다.
 *
 * <p>응답을 신뢰하지 않고 필요한 필드의 존재를 확인한 뒤 쓴다(security.md S7).
 */
@Component
@RequiredArgsConstructor
public class ProjectServiceProjectOwnershipClient implements ProjectOwnershipClient {

    private final RestClient projectServiceRestClient;

    @Override
    public Optional<UUID> findSellerId(UUID projectId) {
        try {
            ProjectDetail detail = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    // 없는 프로젝트는 예외가 아니라 empty다 — 호출 측이 404로 바꾼다.
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                    .body(ProjectDetail.class);
            if (detail == null || detail.seller() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(detail.seller().sellerId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record ProjectDetail(UUID projectId, Seller seller) {
    }

    private record Seller(UUID sellerId) {
    }
}
