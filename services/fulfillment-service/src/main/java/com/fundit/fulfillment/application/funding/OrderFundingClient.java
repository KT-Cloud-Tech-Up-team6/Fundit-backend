package com.fundit.fulfillment.application.funding;

import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/fundings/{fundingId}}) 아웃바운드 포트.
 * payment-service {@code OrderFundingClient}가 이미 같은 엔드포인트를 호출 중이지만, 그
 * 엔드포인트가 실제로는 order-service에 아직 구현돼 있지 않다(확인함) — payment-service 쪽도
 * 동일하게 스텁 상태이므로 fulfillment-service만의 신규 블로커는 아니다.
 *
 * <p>서비스 경계를 넘는 계약이라도 모듈로 공유하지 않는다(각 서비스가 필요한 필드만 담아
 * 자기 포트를 따로 정의) — fulfillment-service는 {@code projectId}(FULFILLMENT-006/008이
 * shipments 없는 funding의 프로젝트를 알아내는 데 사용)와 {@code memberId}(FULFILLMENT-009
 * 구매자 소유권 대조)만 필요하다.
 */
public interface OrderFundingClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetch(Long fundingId);

    record FundingSnapshot(Long projectId, UUID memberId) {
    }
}
