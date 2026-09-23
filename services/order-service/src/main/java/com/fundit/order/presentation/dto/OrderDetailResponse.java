package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.Funding;

import java.util.List;
import java.util.UUID;

/**
 * paidAt은 payment-service 소관(결제 완료 시각)이라 order-service는 값을 알지 못해 항상 null이다
 * — application.yml의 non_null 직렬화 설정 덕에 응답 JSON에서는 필드 자체가 생략된다.
 */
public record OrderDetailResponse(
        UUID orderId, String projectTitle, String thumbnailUrl, String status,
        List<OrderLineItemDetailResponse> lineItems, long shippingFee, long discountAmount, long finalAmount,
        ShippingAddressResponse shippingAddress, java.time.Instant paidAt, java.time.Instant paymentExpiresAt,
        List<String> availableActions
) {

    public static OrderDetailResponse from(OrderQueryService.FundingDetail detail) {
        Funding funding = detail.funding();
        var projectSummary = detail.projectSummary();
        return new OrderDetailResponse(
                funding.getPublicId(), funding.getProjectTitle(),
                projectSummary == null ? null : projectSummary.thumbnailUrl(), funding.getStatus().name(),
                funding.getLineItems().stream().map(OrderLineItemDetailResponse::from).toList(),
                funding.getShippingFee(), detail.discountAmount(), detail.finalAmount(),
                ShippingAddressResponse.from(funding.getShippingAddress()), null, funding.getPaymentExpiresAt(),
                detail.availableActions());
    }
}
