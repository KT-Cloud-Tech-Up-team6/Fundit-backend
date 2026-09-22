package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 단순변심 환불신청 요청 — fundingId는 order-service publicId(UUID). */
public record SimpleChangeOfMindRefundRequestV2(@NotNull UUID fundingId) {
}
