package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.tracker.FulfillmentQueryService.StageProgressStatus;
import com.fundit.fulfillment.application.tracker.FulfillmentQueryService.StageSnapshot;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.time.Instant;

public record StageStatusView(FulfillmentStage stage, StageProgressStatus status, Instant plannedStartAt,
                               Instant plannedEndAt, String detailText, Instant updatedAt) {

    public static StageStatusView from(StageSnapshot snapshot) {
        return new StageStatusView(snapshot.stage(), snapshot.status(), snapshot.plannedStartAt(),
                snapshot.plannedEndAt(), snapshot.detailText(), snapshot.updatedAt());
    }
}
