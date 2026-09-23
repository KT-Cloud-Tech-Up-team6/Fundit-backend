package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.presentation.dto.ShipmentRegisterRequest;
import com.fundit.fulfillment.presentation.dto.ShipmentResponseV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FULFILLMENT-006/009/003 v2 — cross-service ID 통일(#69). projectId/fundingId를
 * UUID로 그대로 받는다/돌려준다 — v1({@link ShipmentController})처럼 내부 해석 호출이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}/fundings/{fundingId}/shipment")
@RequiredArgsConstructor
public class ShipmentControllerV2 {

    private final ShipmentService shipmentService;

    @PostMapping
    public ShipmentResponseV2 register(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                        @PathVariable UUID fundingId,
                                        @Valid @RequestBody ShipmentRegisterRequest request) {
        return ShipmentResponseV2.forSeller(shipmentService.registerShipment(projectId, fundingId, user.id(),
                request.carrier(), request.trackingNumber()));
    }

    /**
     * 발송정보 "저장" — 택배사·운송장만 저장하고 발송 처리는 하지 않는다(상태 PREPARING 유지).
     * 발송 처리({@link #register})와 <b>경로를 나눈 것은 의도</b>다 — 같은 URL에 메서드만 다르게
     * 두면 오호출 한 번이 곧 발송 처리가 되고, 접근 로그에서도 둘을 구분할 수 없다.
     */
    @PostMapping("/draft")
    public ShipmentResponseV2 saveDraft(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                         @PathVariable UUID fundingId,
                                         @Valid @RequestBody ShipmentRegisterRequest request) {
        return ShipmentResponseV2.forSeller(shipmentService.saveShippingInfo(projectId, fundingId, user.id(),
                request.carrier(), request.trackingNumber()));
    }

    @GetMapping
    public ShipmentResponseV2 get(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                   @PathVariable UUID fundingId) {
        return ShipmentResponseV2.from(shipmentService.getShipment(projectId, fundingId, user.id()));
    }

    @PostMapping("/confirm-receipt")
    public ShipmentResponseV2 confirmReceipt(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                              @PathVariable UUID fundingId) {
        return ShipmentResponseV2.from(shipmentService.confirmReceipt(projectId, fundingId, user.id()));
    }
}
