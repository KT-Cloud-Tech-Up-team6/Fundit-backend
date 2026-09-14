package com.fundit.fulfillment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** FULFILLMENT-006 — API #5 요청. */
public record ShipmentRegisterRequest(@NotBlank String carrier, @NotBlank String trackingNumber) {
}
