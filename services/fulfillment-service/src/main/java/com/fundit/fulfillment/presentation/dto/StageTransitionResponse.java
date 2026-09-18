package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

/** v1 응답 — projectId(Long)는 더 이상 채울 수 없어 항상 {@code null}이다. */
public record StageTransitionResponse(Long projectId, FulfillmentStage currentStage) {
}
