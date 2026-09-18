package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.tracker.FulfillmentQueryService.ProjectFulfillmentView;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** v2 응답 — projectId가 project-service publicId(UUID)로 채워진다(cross-service ID 통일 #69). */
public record FulfillmentStatusResponseV2(UUID projectId, FulfillmentStage currentStage, Instant lastUpdatedAt,
                                           boolean isUpdateOverdue, List<StageStatusView> stages,
                                           List<ScheduleChangeResponse> scheduleChanges) {

    public static FulfillmentStatusResponseV2 from(ProjectFulfillmentView view) {
        return new FulfillmentStatusResponseV2(view.projectId(), view.currentStage(), view.lastUpdatedAt(),
                view.updateOverdue(), view.stages().stream().map(StageStatusView::from).toList(),
                view.scheduleChanges().stream().map(ScheduleChangeResponse::from).toList());
    }
}
