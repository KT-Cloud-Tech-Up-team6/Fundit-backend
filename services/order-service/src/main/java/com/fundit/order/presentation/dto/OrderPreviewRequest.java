package com.fundit.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** ORDER-002/010 공용 요청 — POST /api/v1/orders/preview, POST /api/v1/orders 동일 바디. */
public record OrderPreviewRequest(
        @NotNull Long projectId,
        @NotEmpty @Valid List<OrderLineItemRequestDto> lineItems,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        List<String> couponCodes
) {
}
