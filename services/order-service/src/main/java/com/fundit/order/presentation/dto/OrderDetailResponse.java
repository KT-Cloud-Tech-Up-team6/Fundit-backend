package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.Funding;

import java.util.List;
import java.util.UUID;

/**
 * {@code paidAt}은 payment-service가 발행하는 {@code payment.completed.v1}의 결제 완료 시각을
 * 이 서비스가 저장해 둔 값이다 — 결제 전이거나, 이 필드가 이벤트에 실리기 전에 결제된 과거 주문은
 * null이다(application.yml의 non_null 직렬화 설정 덕에 응답 JSON에서는 필드가 생략된다).
 *
 * <p>{@code createdAt}은 참여일(주문 생성 시각)이다 — 목록 응답에는 원래 있었고 상세에만 빠져 있었다.
 *
 * <p>{@code progressStage}는 화면 배지용 파생값이고 {@code status}(주문 상태)를 대체하지 않는다.
 */
public record OrderDetailResponse(
        UUID orderId, String projectTitle, String thumbnailUrl, String status, String progressStage,
        List<OrderLineItemDetailResponse> lineItems, long shippingFee, long discountAmount, long finalAmount,
        ShippingAddressResponse shippingAddress, java.time.Instant paidAt, java.time.Instant paymentExpiresAt,
        List<String> availableActions, List<RefundRequestStatusResponse> refundRequests,
        java.time.Instant createdAt
) {

    public static OrderDetailResponse from(OrderQueryService.FundingDetail detail) {
        Funding funding = detail.funding();
        var projectSummary = detail.projectSummary();
        return new OrderDetailResponse(
                funding.getPublicId(), funding.getProjectTitle(),
                projectSummary == null ? null : projectSummary.thumbnailUrl(), funding.getStatus().name(),
                detail.progressStage().name(),
                funding.getLineItems().stream().map(OrderLineItemDetailResponse::from).toList(),
                funding.getShippingFee(), detail.discountAmount(), detail.finalAmount(),
                ShippingAddressResponse.from(funding.getShippingAddress()), funding.getPaidAt(),
                funding.getPaymentExpiresAt(), detail.availableActions(),
                detail.refundRequests().stream().map(RefundRequestStatusResponse::from).toList(),
                funding.getCreatedAt());
    }
}
