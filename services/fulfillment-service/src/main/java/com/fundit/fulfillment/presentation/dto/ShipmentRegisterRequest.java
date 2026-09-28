package com.fundit.fulfillment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FULFILLMENT-006 — API #5 요청. 길이 제한은 DB 컬럼(carrier 50, tracking_number 100)과 같다 — 넘으면 500 대신 400. */
public record ShipmentRegisterRequest(@NotBlank @Size(max = 50) String carrier,
                                      @NotBlank @Size(max = 100) String trackingNumber) {
}
