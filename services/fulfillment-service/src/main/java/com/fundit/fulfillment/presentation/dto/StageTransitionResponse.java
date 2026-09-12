package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

public record StageTransitionResponse(Long projectId, FulfillmentStage currentStage) {
}
