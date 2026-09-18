package com.fundit.fulfillment.presentation.controller;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService;
import com.fundit.fulfillment.presentation.dto.FulfillmentStatusInternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FULFILLMENT-008 — payment-service 연동 내부 API (API #8). 내부 전용 보호는
 * {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalFulfillmentController {

    private final FulfillmentStatusInternalService fulfillmentStatusInternalService;

    /** cross-service ID 통일(#69) — payment v2가 orderId(UUID)로 호출한다. */
    @GetMapping("/internal/fundings/{fundingId}/fulfillment-status")
    public FulfillmentStatusInternalResponse getStatus(@PathVariable UUID fundingId) {
        return FulfillmentStatusInternalResponse.from(fulfillmentStatusInternalService.getStatus(fundingId));
    }

    /** 레거시 — v1 payment가 order-service 내부 PK(Long)로 호출한다. */
    @GetMapping("/internal/fundings/id/{fundingId}/fulfillment-status")
    public FulfillmentStatusInternalResponse getStatusByInternalId(@PathVariable Long fundingId) {
        return FulfillmentStatusInternalResponse.from(fulfillmentStatusInternalService.getStatus(fundingId));
    }
}
