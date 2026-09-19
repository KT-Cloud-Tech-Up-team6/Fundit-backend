package com.fundit.order.application.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ORDER-004 목록 응답의 projectTitle 스냅샷을 채우기 위한 부가 조회(핵심 주문 흐름에 필수는 아님).
 * 실패해도 주문 생성 자체를 막지 않고 빈 값으로 저장한다 — RewardCatalogClient(가격/재고, 필수)와
 * 달리 실패를 DependencyFailureException으로 전파하지 않는다.
 */
public interface ProjectSummaryClient {

    Optional<String> getProjectTitle(UUID projectId);

    /** CATEGORY 스코프 쿠폰 매칭(ORDER-010)에 쓴다. 조회 실패 시 해당 쿠폰만 미적용 처리되도록 빈 값으로 degrade. */
    Optional<String> getCategoryMajor(UUID projectId);

    /** ORDER-004 목록(V03)용 배치 조회 — 창작자명·썸네일. 조회 실패한 건은 결과에서 빠진다. */
    Map<UUID, ProjectSummary> getSummaries(List<UUID> projectIds);

    record ProjectSummary(String title, String thumbnailUrl, String sellerDisplayName) {
    }
}
