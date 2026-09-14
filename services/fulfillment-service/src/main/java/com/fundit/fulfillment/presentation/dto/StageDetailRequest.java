package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** FULFILLMENT-002 — API #3 요청. */
public record StageDetailRequest(@NotNull FulfillmentStage stage, Instant plannedStartAt, Instant plannedEndAt,
                                  @NotBlank String detailText) {
}
