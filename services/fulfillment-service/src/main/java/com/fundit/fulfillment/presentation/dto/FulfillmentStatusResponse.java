package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.tracker.FulfillmentQueryService.ProjectFulfillmentView;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.time.Instant;
import java.util.List;

/** FULFILLMENT-003 — API #1 응답(프로젝트 단위 제작·배송 진행 현황). */
public record FulfillmentStatusResponse(Long projectId, FulfillmentStage currentStage, Instant lastUpdatedAt,
                                         boolean isUpdateOverdue, List<StageStatusView> stages,
                                         List<ScheduleChangeResponse> scheduleChanges) {

    public static FulfillmentStatusResponse from(ProjectFulfillmentView view) {
        return new FulfillmentStatusResponse(view.projectId(), view.currentStage(), view.lastUpdatedAt(),
                view.updateOverdue(), view.stages().stream().map(StageStatusView::from).toList(),
                view.scheduleChanges().stream().map(ScheduleChangeResponse::from).toList());
    }
}
