package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.UUID;

/**
 * v1(레거시) 목록 응답 — cross-service ID 통일(#69) 이후 project-service는 내부 Long PK를
 * 노출하지 않아 {@code projectId}는 항상 {@code null}이다(알려진 한계). 실제 프로젝트 식별자가
 * 필요하면 {@link OrderSummaryResponseV2}(/api/v2/orders)를 쓸 것.
 */
public record OrderSummaryResponse(
        UUID orderId, Long projectId, String projectTitle, String status, long finalAmount, Instant createdAt
) {

    public static OrderSummaryResponse from(Funding funding) {
        return new OrderSummaryResponse(funding.getPublicId(), null, funding.getProjectTitle(),
                funding.getStatus().name(), funding.totalRewardAmount() + funding.getShippingFee(), funding.getCreatedAt());
    }
}
