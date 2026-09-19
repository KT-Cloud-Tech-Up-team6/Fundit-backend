package com.fundit.payment.application.funding;

import java.util.UUID;

/**
 * order-service 내부 API 아웃바운드 포트.
 *
 * <p>primary는 {@link #fetch(UUID)} — {@code GET /internal/orders/{orderId}}(Funding.publicId).
 * v1 컨트롤러 어댑터만 {@link #fetchByInternalId(Long)}({@code GET /internal/fundings/{fundingId}})를
 * 써서 Long PK를 UUID로 해석한다.
 */
public interface OrderFundingClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetch(UUID orderId);

    /**
     * v1 어댑터 전용 — order-service 내부 PK로 조회한 뒤 {@link FundingSnapshot#fundingPublicId()}를
     * 서비스 레이어에 넘긴다.
     */
    FundingSnapshot fetchByInternalId(Long fundingId);

    record FundingSnapshot(
            UUID memberId,
            UUID sellerId,
            String status,
            long finalAmount,
            String orderName,
            Long couponIssuanceId,
            UUID fundingPublicId,
            long shippingFee,
            long discountAmount) {

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }
}
