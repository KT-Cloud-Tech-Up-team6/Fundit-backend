package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.UUID;

/** ORDER-004 목록 응답 — projectId는 project-service의 publicId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record OrderSummaryResponse(
        UUID orderId, UUID projectId, String projectTitle, String status, long finalAmount, Instant createdAt
) {

    public static OrderSummaryResponse from(Funding funding) {
        return new OrderSummaryResponse(funding.getPublicId(), funding.getProjectId(), funding.getProjectTitle(),
                funding.getStatus().name(), funding.totalRewardAmount() + funding.getShippingFee(), funding.getCreatedAt());
    }
}
