package com.fundit.order.presentation.controller;

import com.fundit.order.application.funding.FundingInternalQueryService;
import com.fundit.order.presentation.dto.InternalFundingParticipantsResponse;
import com.fundit.order.presentation.dto.InternalFundingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * payment/fulfillment-service가 호출하는 내부 전용 API. 내부 전용 보호는
 * {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalFundingController {

    private final FundingInternalQueryService fundingInternalQueryService;

    @GetMapping("/internal/fundings/{fundingId}")
    public InternalFundingResponse getFunding(@PathVariable Long fundingId) {
        return InternalFundingResponse.from(fundingInternalQueryService.getSnapshot(fundingId));
    }

    @GetMapping("/internal/projects/{projectId}/funding-participants")
    public InternalFundingParticipantsResponse getFundingParticipants(@PathVariable Long projectId) {
        return new InternalFundingParticipantsResponse(
                fundingInternalQueryService.listGoalAchievedParticipantMemberIds(projectId));
    }
}
