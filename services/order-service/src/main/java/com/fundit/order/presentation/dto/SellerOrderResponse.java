package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.Funding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 판매자 발송 목록 — 소유 프로젝트의 성립 참여 건마다 발송에 필요한 정보를 담는다.
 *
 * <p>{@code shippedAt}은 fulfillment-service {@code shipment.shipped.v1}을 구독해 채우는
 * 캐시 컬럼이라(V10) 발송 처리 직후 잠깐 null일 수 있다 — 발송 처리 직후의 행 상태는 이 목록이
 * 아니라 발송 API 응답으로 갱신할 것. 송장(carrier/trackingNumber)은 fulfillment-service 소유라
 * 여기 없다(판매자용 배치 조회 API를 따로 호출한다).
 */
public record SellerOrderResponse(
        UUID orderId, List<OrderLineItemDetailResponse> lineItems, ShippingAddressResponse shippingAddress,
        Instant shippedAt
) {

    public static SellerOrderResponse from(Funding funding) {
        return new SellerOrderResponse(funding.getPublicId(),
                funding.getLineItems().stream().map(OrderLineItemDetailResponse::from).toList(),
                ShippingAddressResponse.from(funding.getShippingAddress()), funding.getShippedAt());
    }
}
