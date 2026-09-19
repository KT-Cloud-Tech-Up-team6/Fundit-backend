package com.fundit.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * v2 공용 요청 — POST /api/v2/orders/preview, POST /api/v2/orders 동일 바디.
 * {@link OrderPreviewRequest}(v1)와 달리 projectId를 project-service의 publicId(UUID)로
 * 그대로 받는다(cross-service ID 통일 #69) — 클라이언트가 별도 해석 없이 프로젝트 조회
 * 응답의 projectId를 그대로 넘기면 된다.
 */
public record OrderPreviewRequestV2(
        @NotNull UUID projectId,
        @NotEmpty @Valid List<OrderLineItemRequestDto> lineItems,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        List<String> couponCodes
) {
}
