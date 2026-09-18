package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
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

import java.util.UUID;

/**
 * FULFILLMENT-006/009/003 — 펀딩 단위 발송정보·수령확인 API (API #5~7).
 *
 * <p>cross-service ID 통일(#69) 이후에도 이 컨트롤러(v1)는 Long 계약을 유지한다 —
 * 내부 API로 UUID를 먼저 해석한 뒤 UUID 기반 서비스 레이어를 호출한다. UUID를 그대로
 * 받는 신규 클라이언트는 {@link ShipmentControllerV2}를 쓴다. 응답의 fundingId(Long)는
 * 더 이상 채울 수 없어 항상 null이다.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fundings/{fundingId}/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final OrderFundingClient orderFundingClient;

    /** API #5 — 판매자 전용. */
    @PostMapping
    public ShipmentResponse register(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                      @PathVariable Long fundingId, @Valid @RequestBody ShipmentRegisterRequest request) {
        return ShipmentResponse.from(shipmentService.registerShipment(resolveProjectId(projectId),
                resolveFundingId(fundingId), user.id(), request.carrier(), request.trackingNumber()));
    }

    /** API #6 — 구매자 전용. */
    @GetMapping
    public ShipmentResponse get(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                 @PathVariable Long fundingId) {
        return ShipmentResponse.from(shipmentService.getShipment(resolveProjectId(projectId),
                resolveFundingId(fundingId), user.id()));
    }

    /** API #7 — 구매자 전용. */
    @PostMapping("/confirm-receipt")
    public ShipmentResponse confirmReceipt(@LoginUser CurrentUser user, @PathVariable Long projectId,
                                            @PathVariable Long fundingId) {
        return ShipmentResponse.from(shipmentService.confirmReceipt(resolveProjectId(projectId),
                resolveFundingId(fundingId), user.id()));
    }

    private UUID resolveProjectId(Long legacyProjectId) {
        return projectOwnershipClient.getPublicId(legacyProjectId);
    }

    private UUID resolveFundingId(Long legacyFundingId) {
        return orderFundingClient.fetchByInternalId(legacyFundingId).fundingPublicId();
    }
}
