package com.fundit.order.presentation.controller;

import com.fundit.order.application.funding.FundingInternalQueryService;
import com.fundit.order.presentation.dto.InternalFundingParticipantsResponse;
import com.fundit.order.presentation.dto.InternalFundingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * payment/fulfillment-service가 호출하는 내부 전용 API. 내부 전용 보호는
 * {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalFundingController {

    private final FundingInternalQueryService fundingInternalQueryService;

    /** 레거시 — order-service 내부 PK. v1 payment/fulfillment 클라이언트가 쓴다. */
    @GetMapping("/internal/fundings/{fundingId}")
    public InternalFundingResponse getFunding(@PathVariable Long fundingId) {
        return InternalFundingResponse.from(fundingInternalQueryService.getSnapshot(fundingId));
    }

    /** cross-service ID 통일(#69) — 외부 노출 orderId(UUID)로 조회. */
    @GetMapping("/internal/orders/{orderId}")
    public InternalFundingResponse getFundingByOrderId(@PathVariable UUID orderId) {
        return InternalFundingResponse.from(fundingInternalQueryService.getSnapshotByOrderId(orderId));
    }

    @GetMapping("/internal/projects/{projectId}/funding-participants")
    public InternalFundingParticipantsResponse getFundingParticipants(@PathVariable UUID projectId) {
        return new InternalFundingParticipantsResponse(
                fundingInternalQueryService.listGoalAchievedParticipantMemberIds(projectId));
    }
}
