package com.fundit.order.presentation.dto;

import com.fundit.order.application.funding.FundingInternalQueryService.FundingSnapshot;

import java.util.UUID;

/** 내부 전용 — payment/fulfillment-service가 호출하는 펀딩 스냅샷 조회 응답. */
public record InternalFundingResponse(Long projectId, UUID memberId, UUID fundingPublicId) {

    public static InternalFundingResponse from(FundingSnapshot snapshot) {
        return new InternalFundingResponse(snapshot.projectId(), snapshot.memberId(), snapshot.fundingPublicId());
    }
}
