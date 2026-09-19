package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.tracker.FulfillmentQueryService.ProjectFulfillmentView;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;

import java.time.Instant;
import java.util.List;

/**
 * v1 응답 — cross-service ID 통일(#69) 이후 projectId(Long)는 더 이상 채울 수 없어
 * 항상 {@code null}이다(알려진 한계). 실제 식별자가 필요하면 {@link FulfillmentStatusResponseV2}를 쓸 것.
 */
public record FulfillmentStatusResponse(Long projectId, FulfillmentStage currentStage, Instant lastUpdatedAt,
                                         boolean isUpdateOverdue, List<StageStatusView> stages,
                                         List<ScheduleChangeResponse> scheduleChanges) {

    public static FulfillmentStatusResponse from(ProjectFulfillmentView view) {
        return new FulfillmentStatusResponse(null, view.currentStage(), view.lastUpdatedAt(),
                view.updateOverdue(), view.stages().stream().map(StageStatusView::from).toList(),
                view.scheduleChanges().stream().map(ScheduleChangeResponse::from).toList());
    }
}
