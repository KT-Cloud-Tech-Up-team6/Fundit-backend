package com.fundit.fulfillment.presentation.controller;

import com.fundit.fulfillment.application.shipment.ExchangeReshipmentService;
import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService;
import com.fundit.fulfillment.presentation.dto.FulfillmentBatchStatusResponse;
import com.fundit.fulfillment.presentation.dto.FulfillmentStatusInternalResponse;
import com.fundit.fulfillment.presentation.dto.ReshipmentInternalRequest;
import com.fundit.fulfillment.presentation.dto.ReshipmentInternalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final ExchangeReshipmentService exchangeReshipmentService;

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

    /**
     * 교환 재발송 착수 — payment-service가 교환 승인(판매자 귀책) 또는 교환 배송비 결제 완료
     * 직후 호출한다. 배송을 새 사이클로 되돌리고(PREPARING) 판매자의 새 운송장 등록을 기다린다.
     * 같은 {@code refundRequestId}로 재호출되면 상태를 다시 리셋하지 않는다(멱등).
     */
    @PostMapping("/internal/fundings/{fundingId}/reshipments")
    public ReshipmentInternalResponse startReshipment(@PathVariable UUID fundingId,
                                                       @Valid @RequestBody ReshipmentInternalRequest request) {
        return ReshipmentInternalResponse.from(
                exchangeReshipmentService.startReshipment(fundingId, request.refundRequestId()));
    }

    /** order-service 주문 목록(V03/V06) 배치 조회 — 건별 호출(N+1) 방지용. */
    @GetMapping("/internal/fundings/fulfillment-statuses")
    public List<FulfillmentBatchStatusResponse> getStatuses(@RequestParam List<UUID> fundingIds) {
        return fulfillmentStatusInternalService.getStatuses(fundingIds).stream()
                .map(FulfillmentBatchStatusResponse::from)
                .toList();
    }
}
