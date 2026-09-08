package com.fundit.order.application.catalog;

import java.util.Optional;

/**
 * ORDER-004 목록 응답의 projectTitle 스냅샷을 채우기 위한 부가 조회(핵심 주문 흐름에 필수는 아님).
 * 실패해도 주문 생성 자체를 막지 않고 빈 값으로 저장한다 — RewardCatalogClient(가격/재고, 필수)와
 * 달리 실패를 DependencyFailureException으로 전파하지 않는다.
 */
public interface ProjectSummaryClient {

    Optional<String> getProjectTitle(Long projectId);
}
