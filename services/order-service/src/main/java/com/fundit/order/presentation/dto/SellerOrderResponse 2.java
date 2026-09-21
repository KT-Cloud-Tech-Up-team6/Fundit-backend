package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.util.List;
import java.util.UUID;

/** 판매자 발송 목록 — 소유 프로젝트의 성립 참여 건마다 발송에 필요한 정보를 담는다. */
public record SellerOrderResponse(
        UUID orderId, List<OrderLineItemDetailResponse> lineItems, ShippingAddressResponse shippingAddress
) {

    public static SellerOrderResponse from(Funding funding) {
        return new SellerOrderResponse(funding.getPublicId(),
                funding.getLineItems().stream().map(OrderLineItemDetailResponse::from).toList(),
                ShippingAddressResponse.from(funding.getShippingAddress()));
    }
}
