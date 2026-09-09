package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** PAYMENT-011 요청. */
public record SettlementDisputeRequest(@NotBlank String reason, List<String> evidenceUrls) {
}
