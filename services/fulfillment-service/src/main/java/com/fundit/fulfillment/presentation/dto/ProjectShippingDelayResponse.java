package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.ProjectShippingDelayView;

import java.util.UUID;

/**
 * order-service 주문 목록(V03)이 배치로 호출하는 프로젝트 단위 판정 응답.
 *
 * <p>진행 기록 유무({@code hasProgressRecord}, "제작 중" 배지용)도 프로젝트 단위라 펀딩 배치
 * ({@code /internal/fundings/fulfillment-statuses})가 아니라 이 응답에 같이 싣는다.
 */
public record ProjectShippingDelayResponse(UUID projectId, boolean isDelayed, boolean hasProgressRecord) {

    public static ProjectShippingDelayResponse from(ProjectShippingDelayView view) {
        return new ProjectShippingDelayResponse(view.projectId(), view.isDelayed(), view.hasProgressRecord());
    }
}
