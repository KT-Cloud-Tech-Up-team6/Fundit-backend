package com.fundit.fulfillment.presentation.controller;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService;
import com.fundit.fulfillment.presentation.dto.FulfillmentStatusInternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * FULFILLMENT-008 — payment-service 연동 내부 API (API #8). 내부 전용 보호는
 * {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalFulfillmentController {

    private final FulfillmentStatusInternalService fulfillmentStatusInternalService;

    @GetMapping("/internal/fundings/{fundingId}/fulfillment-status")
    public FulfillmentStatusInternalResponse getStatus(@PathVariable Long fundingId) {
        return FulfillmentStatusInternalResponse.from(fulfillmentStatusInternalService.getStatus(fundingId));
    }
}
