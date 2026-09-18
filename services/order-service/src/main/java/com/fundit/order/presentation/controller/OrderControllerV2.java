package com.fundit.order.presentation.controller;

import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderLineItemRequest;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.order.presentation.dto.OrderCreateResponse;
import com.fundit.order.presentation.dto.OrderLineItemRequestDto;
import com.fundit.order.presentation.dto.OrderPreviewRequestV2;
import com.fundit.order.presentation.dto.OrderPreviewResponse;
import com.fundit.order.presentation.dto.OrderSummaryResponseV2;
import com.fundit.order.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ORDER-002/003/004 v2 — cross-service ID 통일(#69). projectId를 project-service의
 * publicId(UUID)로 그대로 받는다/돌려준다 — v1({@link OrderController})처럼 내부 해석 호출이
 * 없다. {@code detail}/{@code cancel}은 orderId(UUID)만 쓰고 projectId가 응답에 없어 v1과
 * 동일 계약이라 여기 따로 두지 않는다({@link OrderController} 그대로 사용).
 */
@RestController
@RequestMapping("/api/v2/orders")
@RequiredArgsConstructor
public class OrderControllerV2 {

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;

    @PostMapping("/preview")
    public OrderPreviewResponse preview(@LoginUser CurrentUser user, @Valid @RequestBody OrderPreviewRequestV2 request) {
        OrderPricingService.PricingResult result = orderPreviewService.preview(
                user.id(), request.projectId(), toLineItems(request.lineItems()), request.couponCodes());
        return OrderPreviewResponse.from(result);
    }

    @PostMapping
    public ResponseEntity<OrderCreateResponse> create(@LoginUser CurrentUser user,
                                                        @Valid @RequestBody OrderPreviewRequestV2 request) {
        OrderCreateService.OrderCreateResult result = orderCreateService.create(user.id(), request.projectId(),
                toLineItems(request.lineItems()), request.shippingAddress().toDomain(), request.couponCodes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderCreateResponse.from(result.funding(), result.finalAmount()));
    }

    @GetMapping
    public PageResponse<OrderSummaryResponseV2> list(@LoginUser CurrentUser user,
                                                       @RequestParam(required = false) FundingStatus status,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderQueryService.listMyOrders(user.id(), status, pageable)
                .map(OrderSummaryResponseV2::from));
    }

    private List<OrderLineItemRequest> toLineItems(List<OrderLineItemRequestDto> dtos) {
        return dtos.stream().map(OrderLineItemRequestDto::toApplication).toList();
    }
}
