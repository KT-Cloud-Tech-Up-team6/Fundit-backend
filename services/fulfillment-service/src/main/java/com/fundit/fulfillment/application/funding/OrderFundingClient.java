package com.fundit.fulfillment.application.funding;

import java.util.UUID;

/**
 * order-service 내부 API 아웃바운드 포트.
 *
 * <p>cross-service ID 통일(#69) 이후 주 경로는 {@code GET /internal/orders/{orderId}}(UUID)다.
 * 레거시 Long PK 조회({@code GET /internal/fundings/{fundingId}})는 v1 어댑터 전용이다.
 *
 * <p>서비스 경계를 넘는 계약이라도 모듈로 공유하지 않는다(각 서비스가 필요한 필드만 담아
 * 자기 포트를 따로 정의) — fulfillment-service는 {@code projectId}(project-service publicId,
 * FULFILLMENT-006/008이 shipments 없는 funding의 프로젝트를 알아내는 데 사용),
 * {@code memberId}(FULFILLMENT-009 구매자 소유권 대조), {@code fundingPublicId}(알림 relatedUrl
 * 조립용 — 외부 노출 식별자는 항상 publicId)가 필요하다.
 */
public interface OrderFundingClient {

    /**
     * 외부 노출 orderId(UUID) 기준 조회 — {@code GET /internal/orders/{orderId}}.
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetch(UUID orderId);

    /**
     * 레거시 Long PK 기준 조회 — {@code GET /internal/fundings/{fundingId}}. v1 어댑터 전용.
     *
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetchByInternalId(Long fundingId);

    /** {@code projectId}는 project-service publicId(UUID)다. */
    record FundingSnapshot(UUID projectId, UUID memberId, UUID fundingPublicId) {
    }
}
