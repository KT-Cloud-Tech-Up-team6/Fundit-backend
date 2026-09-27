package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.ProjectShippingDelayView;

import java.util.UUID;

/** order-service 주문 목록(V03)이 배치로 호출하는 프로젝트 단위 발송지연 판정 응답. */
public record ProjectShippingDelayResponse(UUID projectId, boolean isDelayed) {

    public static ProjectShippingDelayResponse from(ProjectShippingDelayView view) {
        return new ProjectShippingDelayResponse(view.projectId(), view.isDelayed());
    }
}
