package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.tracker.FulfillmentQueryService;
import com.fundit.fulfillment.application.tracker.ScheduleChangeService;
import com.fundit.fulfillment.application.tracker.StageProgressService;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.presentation.dto.FulfillmentStatusResponseV2;
import com.fundit.fulfillment.presentation.dto.ScheduleChangeRequest;
import com.fundit.fulfillment.presentation.dto.ScheduleChangeResponse;
import com.fundit.fulfillment.presentation.dto.StageDetailRequest;
import com.fundit.fulfillment.presentation.dto.StageDetailResponse;
import com.fundit.fulfillment.presentation.dto.StageTransitionRequest;
import com.fundit.fulfillment.presentation.dto.StageTransitionResponseV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FULFILLMENT-002/003/005 v2 — cross-service ID 통일(#69). projectId를 project-service의
 * publicId(UUID)로 그대로 받는다/돌려준다 — v1({@link FulfillmentController})처럼 내부 해석
 * 호출이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}/fulfillment")
@RequiredArgsConstructor
public class FulfillmentControllerV2 {

    private final FulfillmentQueryService fulfillmentQueryService;
    private final StageProgressService stageProgressService;
    private final ScheduleChangeService scheduleChangeService;

    @GetMapping
    public FulfillmentStatusResponseV2 getFulfillment(@PathVariable UUID projectId) {
        return FulfillmentStatusResponseV2.from(fulfillmentQueryService.getProjectFulfillment(projectId));
    }

    @PatchMapping("/stage")
    public StageTransitionResponseV2 transitionStage(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                                       @Valid @RequestBody StageTransitionRequest request) {
        FulfillmentTracker tracker = stageProgressService.transitionStage(projectId, user.id(), request.stage());
        return new StageTransitionResponseV2(tracker.getProjectId(), tracker.getCurrentStage());
    }

    @PostMapping("/stage-details")
    public StageDetailResponse registerStageDetail(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                                     @Valid @RequestBody StageDetailRequest request) {
        FulfillmentStageDetailJpaEntity saved = stageProgressService.registerStageDetail(projectId, user.id(),
                request.stage(), request.plannedStartAt(), request.plannedEndAt(), request.detailText(),
                request.photoUrls());
        return StageDetailResponse.from(saved);
    }

    @PostMapping("/schedule-changes")
    public ScheduleChangeResponse registerScheduleChange(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                                           @Valid @RequestBody ScheduleChangeRequest request) {
        FulfillmentScheduleChangeJpaEntity saved = scheduleChangeService.registerScheduleChange(projectId, user.id(),
                request.stage(), request.reasonType(), request.reasonDetail(), request.newPlannedDate());
        return ScheduleChangeResponse.from(saved);
    }
}
