package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.presentation.dto.ShipmentResponseV2;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 판매자 발송 목록 화면용 배치 조회. order-service 목록(`GET /api/v1/projects/{id}/orders`)이
 * 돌려준 한 페이지의 fundingId를 그대로 넘겨 행별 발송상태·송장을 한 번에 받는다.
 * 단건 조회({@link ShipmentControllerV2#get})는 구매자 전용이라 판매자가 쓸 수 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}/shipments")
@RequiredArgsConstructor
public class SellerShipmentControllerV2 {

    /** 목록 한 페이지분을 채우는 용도 — 페이지 크기(기본 20)보다 넉넉하되 무제한은 아니게. */
    private static final int MAX_FUNDING_IDS = 100;

    private final ShipmentService shipmentService;

    @GetMapping
    public List<ShipmentResponseV2> listForSeller(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                                    @RequestParam List<UUID> fundingIds) {
        if (fundingIds.size() > MAX_FUNDING_IDS) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "한 번에 조회할 수 있는 fundingIds는 " + MAX_FUNDING_IDS + "건까지입니다.");
        }
        return shipmentService.listForSeller(projectId, user.id(), fundingIds).stream()
                .map(ShipmentResponseV2::forSeller)
                .toList();
    }
}
