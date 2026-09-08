package com.fundit.order.presentation.controller;

import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderLineItemRequest;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.infrastructure.security.CurrentMember;
import com.fundit.order.presentation.dto.OrderCancelResponse;
import com.fundit.order.presentation.dto.OrderCreateResponse;
import com.fundit.order.presentation.dto.OrderDetailResponse;
import com.fundit.order.presentation.dto.OrderLineItemRequestDto;
import com.fundit.order.presentation.dto.OrderPreviewRequest;
import com.fundit.order.presentation.dto.OrderPreviewResponse;
import com.fundit.order.presentation.dto.OrderSummaryResponse;
import com.fundit.order.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;

    /** ORDER-002/010 — 결제금액 계산(미리보기, 쿠폰 적용/해제 포함). 비영속. */
    @PostMapping("/preview")
    public OrderPreviewResponse preview(@CurrentMember UUID memberId, @Valid @RequestBody OrderPreviewRequest request) {
        OrderPricingService.PricingResult result = orderPreviewService.preview(
                memberId, request.projectId(), toLineItems(request.lineItems()), request.couponCodes());
        return OrderPreviewResponse.from(result);
    }

    /** ORDER-003 — 펀딩 주문 생성(재고 검증/차감). */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> create(@CurrentMember UUID memberId,
                                                        @Valid @RequestBody OrderPreviewRequest request) {
        OrderCreateService.OrderCreateResult result = orderCreateService.create(memberId, request.projectId(),
                toLineItems(request.lineItems()), request.shippingAddress().toDomain(), request.couponCodes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderCreateResponse.from(result.funding(), result.finalAmount()));
    }

    /** ORDER-004 — 내 펀딩 참여 목록 조회. */
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@CurrentMember UUID memberId,
                                                     @RequestParam(required = false) FundingStatus status,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderQueryService.listMyOrders(memberId, status, pageable)
                .map(OrderSummaryResponse::from));
    }

    /** ORDER-005 — 개별 펀딩 참여 상세 조회. */
    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(@CurrentMember UUID memberId, @PathVariable UUID orderId) {
        return OrderDetailResponse.from(orderQueryService.getDetail(memberId, orderId));
    }

    /** ORDER-014 — 참여 취소(단순변심). */
    @PostMapping("/{orderId}/cancel")
    public OrderCancelResponse cancel(@CurrentMember UUID memberId, @PathVariable UUID orderId) {
        return OrderCancelResponse.from(orderCancelService.cancel(memberId, orderId));
    }

    private List<OrderLineItemRequest> toLineItems(List<OrderLineItemRequestDto> dtos) {
        return dtos.stream().map(OrderLineItemRequestDto::toApplication).toList();
    }
}
