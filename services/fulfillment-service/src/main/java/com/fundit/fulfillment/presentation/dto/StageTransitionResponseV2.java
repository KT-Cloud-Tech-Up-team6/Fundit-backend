package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.util.UUID;

/** v2 응답 — projectId가 project-service publicId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record StageTransitionResponseV2(UUID projectId, FulfillmentStage currentStage) {
}
