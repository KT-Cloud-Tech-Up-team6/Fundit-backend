package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID orderId, Long projectId, String projectTitle, String status, long finalAmount, Instant createdAt
) {

    public static OrderSummaryResponse from(Funding funding) {
        return new OrderSummaryResponse(funding.getPublicId(), funding.getProjectId(), funding.getProjectTitle(),
                funding.getStatus().name(), funding.totalRewardAmount() + funding.getShippingFee(), funding.getCreatedAt());
    }
}
