package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;

import java.time.Instant;

public record StageDetailResponse(Long stageDetailId, FulfillmentStage stage, String detailText, Instant updatedAt) {

    public static StageDetailResponse from(FulfillmentStageDetailJpaEntity entity) {
        return new StageDetailResponse(entity.getId(), FulfillmentStage.valueOf(entity.getStage()),
                entity.getDetailText(), entity.getUpdatedAt());
    }
}
