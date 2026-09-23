package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
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

import java.util.UUID;

/**
 * FULFILLMENT-002/003/005 — 프로젝트 단위 제작·배송 진행 현황 API (API #1~4).
 *
 * <p>cross-service ID 통일(#69) 이후에도 이 컨트롤러(v1)는 {@code projectId: Long} 계약을
 * 유지한다 — project-service 내부 API로 UUID를 먼저 해석한 뒤 UUID 기반 서비스 레이어를
 * 호출한다. UUID를 그대로 받는 신규 클라이언트는 {@link FulfillmentControllerV2}를 쓴다.
 * 응답의 projectId(Long)는 더 이상 채울 수 없어 항상 null이다.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentQueryService fulfillmentQueryService;
    private final StageProgressService stageProgressService;
    private final ScheduleChangeService scheduleChangeService;
    private final ProjectOwnershipClient projectOwnershipClient;

    /** API #1 — 공통 조회, 인증 불필요. */
    @GetMapping
    public FulfillmentStatusResponse getFulfillment(@PathVariable Long projectId) {
        return FulfillmentStatusResponse.from(fulfillmentQueryService.getProjectFulfillment(resolveProjectId(projectId)));
    }

    /** API #2 — 판매자 전용. */
    @PatchMapping("/stage")
    public StageTransitionResponse transitionStage(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                     @Valid @RequestBody StageTransitionRequest request) {
        FulfillmentTracker tracker = stageProgressService.transitionStage(
                resolveProjectId(projectId), user.id(), request.stage());
        return new StageTransitionResponse(null, tracker.getCurrentStage());
    }

    /** API #3 — 판매자 전용. */
    @PostMapping("/stage-details")
    public StageDetailResponse registerStageDetail(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                     @Valid @RequestBody StageDetailRequest request) {
        FulfillmentStageDetailJpaEntity saved = stageProgressService.registerStageDetail(
                resolveProjectId(projectId), user.id(),
                request.stage(), request.plannedStartAt(), request.plannedEndAt(), request.detailText(),
                request.photoUrls());
        return StageDetailResponse.from(saved);
    }

    /** API #4 — 판매자 전용. */
    @PostMapping("/schedule-changes")
    public ScheduleChangeResponse registerScheduleChange(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                                           @Valid @RequestBody ScheduleChangeRequest request) {
        FulfillmentScheduleChangeJpaEntity saved = scheduleChangeService.registerScheduleChange(
                resolveProjectId(projectId), user.id(),
                request.stage(), request.reasonType(), request.reasonDetail(), request.newPlannedDate());
        return ScheduleChangeResponse.from(saved);
    }

    private UUID resolveProjectId(Long legacyProjectId) {
        return projectOwnershipClient.getPublicId(legacyProjectId);
    }
}
