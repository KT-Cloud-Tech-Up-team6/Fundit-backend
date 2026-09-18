package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderLineItemRequest;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
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

/**
 * ORDER-002/003/004/005/014.
 *
 * <p>cross-service ID 통일(#69) 이후에도 이 컨트롤러(v1)는 {@code projectId: Long} 계약을
 * 유지한다 — project-service 내부 API로 UUID를 먼저 해석한 뒤 UUID 기반 서비스 레이어를
 * 호출한다. UUID를 그대로 받는 신규 클라이언트는 {@link OrderControllerV2}(/api/v2/orders)를
 * 쓴다. {@code GET /api/v1/orders}(목록)는 응답의 projectId(Long)를 더 이상 채울 수 없어
 * (project-service가 내부 PK를 노출하지 않음) 항상 null이다 — 알려진 한계, 실제 값이 필요하면
 * v2 목록 엔드포인트를 쓸 것.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final ProjectOwnershipClient projectOwnershipClient;

    /** ORDER-002/010 — 결제금액 계산(미리보기, 쿠폰 적용/해제 포함). 비영속. */
    @PostMapping("/preview")
    public OrderPreviewResponse preview(@LoginUser CurrentUser user, @Valid @RequestBody OrderPreviewRequest request) {
        UUID projectPublicId = resolveProjectId(request.projectId());
        OrderPricingService.PricingResult result = orderPreviewService.preview(
                user.id(), projectPublicId, toLineItems(request.lineItems()), request.couponCodes());
        return OrderPreviewResponse.from(result);
    }

    /** ORDER-003 — 펀딩 주문 생성(재고 검증/차감). */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> create(@LoginUser CurrentUser user,
                                                        @Valid @RequestBody OrderPreviewRequest request) {
        UUID projectPublicId = resolveProjectId(request.projectId());
        OrderCreateService.OrderCreateResult result = orderCreateService.create(user.id(), projectPublicId,
                toLineItems(request.lineItems()), request.shippingAddress().toDomain(), request.couponCodes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderCreateResponse.from(result.funding(), result.finalAmount()));
    }

    /** ORDER-004 — 내 펀딩 참여 목록 조회. */
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@LoginUser CurrentUser user,
                                                     @RequestParam(required = false) FundingStatus status,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderQueryService.listMyOrders(user.id(), status, pageable)
                .map(OrderSummaryResponse::from));
    }

    /** ORDER-005 — 개별 펀딩 참여 상세 조회. */
    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(@LoginUser CurrentUser user, @PathVariable UUID orderId) {
        return OrderDetailResponse.from(orderQueryService.getDetail(user.id(), orderId));
    }

    /** ORDER-014 — 참여 취소(단순변심). */
    @PostMapping("/{orderId}/cancel")
    public OrderCancelResponse cancel(@LoginUser CurrentUser user, @PathVariable UUID orderId) {
        return OrderCancelResponse.from(orderCancelService.cancel(user.id(), orderId));
    }

    private UUID resolveProjectId(Long legacyProjectId) {
        return projectOwnershipClient.findPublicId(legacyProjectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private List<OrderLineItemRequest> toLineItems(List<OrderLineItemRequestDto> dtos) {
        return dtos.stream().map(OrderLineItemRequestDto::toApplication).toList();
    }
}
