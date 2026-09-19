package com.fundit.fulfillment.application.project;

import java.util.UUID;

/**
 * 판매자 소유권 검증용 아웃바운드 포트(security.md S4 — 식별자만으로 접근을 허용하지 않고
 * 소유권을 서버에서 대조). UUID(publicId) 기준 조회는 project-service 공개 상세 API
 * ({@code GET /api/v1/projects/{projectId}})를 쓰고, 레거시 Long PK 조회는 내부 전용 API
 * ({@code GET /internal/projects/{projectId}})를 쓴다 — v1 어댑터와 Kafka Long projectId 해석용.
 */
public interface ProjectOwnershipClient {

    /**
     * 레거시 Long projectId 기준 조회 — {@code GET /internal/projects/{projectId}}.
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    UUID getSellerId(Long projectId);

    /**
     * 알림 relatedUrl 조립·v1 어댑터용 — 레거시 Long → publicId(UUID).
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    UUID getPublicId(Long projectId);

    /**
     * UUID(publicId) 기준 조회 — {@code GET /api/v1/projects/{projectId}}.
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    UUID getSellerId(UUID projectId);

    /**
     * UUID(publicId) 기준 공개 식별자 조회 — {@code GET /api/v1/projects/{projectId}}.
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    UUID getPublicId(UUID projectId);
}
