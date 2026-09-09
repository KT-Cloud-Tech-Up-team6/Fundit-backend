package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.UUID;

public record OrderCreateResponse(UUID orderId, String status, long finalAmount, Instant paymentExpiresAt) {

    public static OrderCreateResponse from(Funding funding, long finalAmount) {
        return new OrderCreateResponse(funding.getPublicId(), funding.getStatus().name(), finalAmount,
                funding.getPaymentExpiresAt());
    }
}
