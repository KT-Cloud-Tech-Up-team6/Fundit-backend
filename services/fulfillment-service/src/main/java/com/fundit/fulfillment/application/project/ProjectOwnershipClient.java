package com.fundit.fulfillment.application.project;

import java.util.UUID;

/**
 * 판매자 소유권 검증용 아웃바운드 포트(security.md S4 — 식별자만으로 접근을 허용하지 않고
 * 소유권을 서버에서 대조). project-service는 공개 상세 API(`GET /api/v1/projects/{projectId}`)를
 * {@code publicId}(UUID) 기준으로만 노출하는데, fulfillment-service를 포함한 order-service 계열
 * 서비스들은 내부 {@code Long} projectId를 쓴다(order-service {@code Project.id}(Long) vs
 * {@code publicId}(UUID) 확인함) — 타입이 달라 그 경로로는 조회할 수 없고, project-service에
 * {@code Long id} 기준 내부 조회 엔드포인트가 별도로 필요하다(현재 없음, 연동 이슈로 남김).
 */
public interface ProjectOwnershipClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    UUID getSellerId(Long projectId);
}
