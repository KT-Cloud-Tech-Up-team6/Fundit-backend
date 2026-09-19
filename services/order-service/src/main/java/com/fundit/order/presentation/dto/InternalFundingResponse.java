package com.fundit.order.presentation.dto;

import com.fundit.order.application.funding.FundingInternalQueryService.FundingSnapshot;

import java.util.UUID;

/**
 * 내부 전용 — payment/fulfillment-service가 호출하는 펀딩 스냅샷 조회 응답.
 * {@code projectId}는 project-service publicId(UUID)다. {@code fundingId}는 내부 PK,
 * {@code fundingPublicId}는 외부 노출 orderId(UUID)다.
 */
public record InternalFundingResponse(Long fundingId, UUID projectId, UUID memberId, UUID fundingPublicId) {

    public static InternalFundingResponse from(FundingSnapshot snapshot) {
        return new InternalFundingResponse(snapshot.fundingId(), snapshot.projectId(),
                snapshot.memberId(), snapshot.fundingPublicId());
    }
}
