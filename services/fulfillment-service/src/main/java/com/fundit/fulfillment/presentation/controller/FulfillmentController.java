package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.tracker.FulfillmentQueryService;
import com.fundit.fulfillment.application.tracker.ScheduleChangeService;
import com.fundit.fulfillment.application.tracker.StageProgressService;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.presentation.dto.FulfillmentStatusResponse;
import com.fundit.fulfillment.presentation.dto.ScheduleChangeRequest;
import com.fundit.fulfillment.presentation.dto.ScheduleChangeResponse;
import com.fundit.fulfillment.presentation.dto.StageDetailRequest;
import com.fundit.fulfillment.presentation.dto.StageDetailResponse;
import com.fundit.fulfillment.presentation.dto.StageTransitionRequest;
import com.fundit.fulfillment.presentation.dto.StageTransitionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FULFILLMENT-002/003/005 — 프로젝트 단위 제작·배송 진행 현황 API (API #1~4). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentQueryService fulfillmentQueryService;
    private final StageProgressService stageProgressService;
    private final ScheduleChangeService scheduleChangeService;

    /** API #1 — 공통 조회, 인증 불필요. */
    @GetMapping
    public FulfillmentStatusResponse getFulfillment(@PathVariable Long projectId) {
        return FulfillmentStatusResponse.from(fulfillmentQueryService.getProjectFulfillment(projectId));
    }

    /** API #2 — 판매자 전용. */
    @PatchMapping("/stage")
    public StageTransitionResponse transitionStage(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                     @Valid @RequestBody StageTransitionRequest request) {
        FulfillmentTracker tracker = stageProgressService.transitionStage(projectId, user.id(), request.stage());
        return new StageTransitionResponse(tracker.getProjectId(), tracker.getCurrentStage());
    }

    /** API #3 — 판매자 전용. */
    @PostMapping("/stage-details")
    public StageDetailResponse registerStageDetail(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                     @Valid @RequestBody StageDetailRequest request) {
        FulfillmentStageDetailJpaEntity saved = stageProgressService.registerStageDetail(projectId, user.id(),
                request.stage(), request.plannedStartAt(), request.plannedEndAt(), request.detailText());
        return StageDetailResponse.from(saved);
    }

    /** API #4 — 판매자 전용. */
    @PostMapping("/schedule-changes")
    public ScheduleChangeResponse registerScheduleChange(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                           @Valid @RequestBody ScheduleChangeRequest request) {
        FulfillmentScheduleChangeJpaEntity saved = scheduleChangeService.registerScheduleChange(projectId, user.id(),
                request.stage(), request.reasonType(), request.reasonDetail(), request.newPlannedDate());
        return ScheduleChangeResponse.from(saved);
    }
}
