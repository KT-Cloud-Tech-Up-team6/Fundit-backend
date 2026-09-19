package com.fundit.order.application.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * ORDER-008 — 메이커 쿠폰 발급 전 "본인 소유 프로젝트인지" 서버 검증(security.md S4)을 위해
 * project-service에서 프로젝트의 판매자 id를 조회한다. 소유권 검증은 보안에 직결되므로
 * ProjectSummaryClient(제목 조회, 실패해도 degrade)와 달리 실패 시 예외를 그대로 전파한다.
 */
public interface ProjectOwnershipClient {

    /**
     * 레거시 Long projectId 기준 조회 — project-service의 내부 전용 API
     * ({@code GET /internal/projects/{projectId}})를 쓴다. 아직 project-service가 발행하지
     * 않는 project.funding-deadline-reached.v1 이벤트(Long projectId)를 구독하는 경로에서만 쓴다.
     */
    Optional<UUID> findSellerId(Long projectId);

    /**
     * cross-service ID 통일(#69) 이후 REST 요청에서 받는 UUID(publicId) 기준 조회 — project-service의
     * 공개 상세 API({@code GET /api/v1/projects/{projectId}})를 쓴다.
     */
    Optional<UUID> findSellerId(UUID projectId);

    /**
     * 레거시 Long projectId → publicId(UUID) 변환 — /api/v1(구버전, Long을 계속 받는) 엔드포인트가
     * 내부적으로 UUID 기반 서비스 레이어를 호출하기 전에 쓰거나, fundings.project_public_id
     * 백필 배치가 쓴다. project-service의 내부 전용 API를 쓴다.
     */
    Optional<UUID> findPublicId(Long projectId);
}
