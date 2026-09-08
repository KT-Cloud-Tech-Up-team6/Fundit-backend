package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.util.UUID;

public record OrderCancelResponse(UUID orderId, String status) {

    public static OrderCancelResponse from(Funding funding) {
        return new OrderCancelResponse(funding.getPublicId(), funding.getStatus().name());
    }
}
