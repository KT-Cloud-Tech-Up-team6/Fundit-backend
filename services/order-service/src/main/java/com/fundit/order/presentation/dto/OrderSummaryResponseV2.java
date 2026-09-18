package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.UUID;

/** v2 목록 응답 — projectId가 project-service의 publicId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record OrderSummaryResponseV2(
        UUID orderId, UUID projectId, String projectTitle, String status, long finalAmount, Instant createdAt
) {

    public static OrderSummaryResponseV2 from(Funding funding) {
        return new OrderSummaryResponseV2(funding.getPublicId(), funding.getProjectId(), funding.getProjectTitle(),
                funding.getStatus().name(), funding.totalRewardAmount() + funding.getShippingFee(), funding.getCreatedAt());
    }
}
