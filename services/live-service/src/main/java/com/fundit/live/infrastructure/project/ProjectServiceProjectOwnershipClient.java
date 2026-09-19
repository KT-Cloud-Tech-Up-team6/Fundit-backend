package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.project.ProjectOwnershipClient;
import lombok.RequiredArgsConstructor;
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
                    // 404만 "없는 프로젝트"로 흘린다 — 호출 측이 그걸 404로 바꾼다.
                    // 401·403·429까지 삼키면 우리 인증 실패가 "프로젝트 없음"으로 뭉개져
                    // 원인을 찾을 수 없다. 나머지 4xx는 아래 RestClientException으로 떨어진다.
                    .onStatus(status -> status.value() == 404, (req, res) -> { })
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
