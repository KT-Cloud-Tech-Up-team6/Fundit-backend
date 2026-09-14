package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.presentation.dto.ShipmentRegisterRequest;
import com.fundit.fulfillment.presentation.dto.ShipmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FULFILLMENT-006/009/003 — 펀딩 단위 발송정보·수령확인 API (API #5~7). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fundings/{fundingId}/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    /** API #5 — 판매자 전용. */
    @PostMapping
    public ShipmentResponse register(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                      @PathVariable Long fundingId, @Valid @RequestBody ShipmentRegisterRequest request) {
        return ShipmentResponse.from(shipmentService.registerShipment(projectId, fundingId, user.id(),
                request.carrier(), request.trackingNumber()));
    }

    /** API #6 — 구매자 전용. */
    @GetMapping
    public ShipmentResponse get(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                 @PathVariable Long fundingId) {
        return ShipmentResponse.from(shipmentService.getShipment(projectId, fundingId, user.id()));
    }

    /** API #7 — 구매자 전용. */
    @PostMapping("/confirm-receipt")
    public ShipmentResponse confirmReceipt(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                            @PathVariable Long fundingId) {
        return ShipmentResponse.from(shipmentService.confirmReceipt(projectId, fundingId, user.id()));
    }
}
