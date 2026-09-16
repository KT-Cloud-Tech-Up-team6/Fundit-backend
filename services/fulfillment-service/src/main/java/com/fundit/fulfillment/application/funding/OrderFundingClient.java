package com.fundit.fulfillment.application.funding;

import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/fundings/{fundingId}}) 아웃바운드 포트.
 * payment-service {@code OrderFundingClient}가 이미 같은 엔드포인트를 호출 중이지만 필요한
 * 필드가 더 많다(finalAmount 등) — 그 계약은 별도 연동 이슈로 남아 있고, 이 서비스는
 * order-service가 실제로 제공하는 필드 중 필요한 것만 받는다.
 *
 * <p>서비스 경계를 넘는 계약이라도 모듈로 공유하지 않는다(각 서비스가 필요한 필드만 담아
 * 자기 포트를 따로 정의) — fulfillment-service는 {@code projectId}(FULFILLMENT-006/008이
 * shipments 없는 funding의 프로젝트를 알아내는 데 사용), {@code memberId}(FULFILLMENT-009
 * 구매자 소유권 대조), {@code fundingPublicId}(알림 relatedUrl 조립용 — 외부 노출 식별자는
 * 항상 publicId, 내부 PK를 URL에 쓰지 않는다)가 필요하다.
 */
public interface OrderFundingClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetch(Long fundingId);

    record FundingSnapshot(Long projectId, UUID memberId, UUID fundingPublicId) {
    }
}
