package com.fundit.order.presentation.dto;

import com.fundit.order.application.funding.FundingInternalQueryService.FundingSnapshot;

import java.util.UUID;

/**
 * 내부 전용 — payment/fulfillment-service가 호출하는 펀딩 스냅샷 조회 응답.
 * {@code projectId}는 project-service publicId(UUID)다. {@code fundingId}는 내부 PK,
 * {@code fundingPublicId}는 외부 노출 orderId(UUID)다. payment-service {@code HttpOrderFundingClient}
 * (PAYMENT-001)가 이 필드 이름 그대로 역직렬화하므로 필드명을 바꾸면 그쪽 연동이 깨진다.
 */
public record InternalFundingResponse(Long fundingId, UUID projectId, UUID memberId, UUID fundingPublicId,
                                       UUID sellerId, String status, long finalAmount, String orderName,
                                       Long couponIssuanceId) {

    public static InternalFundingResponse from(FundingSnapshot snapshot) {
        return new InternalFundingResponse(snapshot.fundingId(), snapshot.projectId(),
                snapshot.memberId(), snapshot.fundingPublicId(), snapshot.sellerId(), snapshot.status(),
                snapshot.finalAmount(), snapshot.orderName(), snapshot.couponIssuanceId());
    }
}
