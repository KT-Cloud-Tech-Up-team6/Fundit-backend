package com.fundit.order.presentation.controller;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.application.order.LiveOrderStatsService;
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
import com.fundit.order.presentation.dto.LiveOrderStatsResponse;
import com.fundit.order.presentation.dto.OrderLineItemRequestDto;
import com.fundit.order.presentation.dto.OrderPreviewRequest;
import com.fundit.order.presentation.dto.OrderPreviewResponse;
import com.fundit.order.presentation.dto.OrderSummaryResponse;
import com.fundit.order.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * ORDER-002/003/004/005/014.
 *
 * <p>cross-service ID 통일(#69) — projectId는 project-service의 publicId(UUID)를 그대로
 * 받는다/돌려준다. 클라이언트가 프로젝트 조회 응답의 projectId를 그대로 넘기면 되고, 서버는
 * 내부 해석 호출 없이 바로 UUID 기반 서비스 레이어를 호출한다.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderPreviewService orderPreviewService;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final LiveOrderStatsService liveOrderStatsService;
    private final LiveStatusClient liveStatusClient;

    /** ORDER-002/010 — 결제금액 계산(미리보기, 쿠폰 적용/해제 포함). 비영속. */
    @PostMapping("/preview")
    public OrderPreviewResponse preview(@LoginUser CurrentUser user, @Valid @RequestBody OrderPreviewRequest request) {
        OrderPricingService.PricingResult result = orderPreviewService.preview(
                user.id(), request.projectId(), toLineItems(request.lineItems()), request.couponCodes(),
                request.resolveAutoApplyBestCoupon());
        return OrderPreviewResponse.from(result);
    }

    /**
     * ORDER-003 — 펀딩 주문 생성(재고 검증/차감). {@code Idempotency-Key} 헤더는 선택값이다 — 보내면
     * 같은 회원이 같은 키로 재요청했을 때 새 주문을 만들지 않고 기존 주문을 그대로 돌려준다(201 대신
     * 200). 응답을 못 받은 클라이언트가 재시도할 때 중복 주문/중복 재고차감을 막기 위함이다.
     */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> create(@LoginUser CurrentUser user,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        @Valid @RequestBody OrderPreviewRequest request) {
        String idempotencyRequestHash = idempotencyKey == null ? null : hashRequest(request);
        OrderCreateService.OrderCreateResult result = orderCreateService.create(user.id(), request.projectId(),
                toLineItems(request.lineItems()), request.shippingAddress().toDomain(), request.couponCodes(),
                request.resolveAutoApplyBestCoupon(), idempotencyKey, idempotencyRequestHash,
                findLiveSessionId(request.projectId()));
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(OrderCreateResponse.from(result.funding(), result.finalAmount()));
    }

    /**
     * 방송 중 생성된 주문에 붙일 세션 꼬리표. 트랜잭션에 들어가기 전에 여기서 조회한다 —
     * 주문 생성 트랜잭션 안에서 외부 호출을 하면 DB 커넥션을 쥔 채 응답을 기다리게 된다.
     *
     * <p>조회 실패는 주문을 막지 않는다(확정 계약 5번): 라이브ID는 나중에 집계에 쓰는 꼬리표라
     * 못 달아도 본 거래는 진행한다. 쿠폰(게이트)과 반대 정책이다.
     */
    private Long findLiveSessionId(UUID projectId) {
        try {
            return liveStatusClient.findActiveByProject(projectId)
                    .map(LiveStatusClient.LiveStatus::sessionId)
                    .orElse(null);
        } catch (DependencyFailureException e) {
            log.warn("live-service 조회 실패 — liveSessionId 없이 주문을 생성합니다. projectId={}", projectId, e);
            return null;
        }
    }

    /** 같은 Idempotency-Key에 다른 본문이 오는 것을 구분하기 위한 요청 해시(SHA-256). */
    private String hashRequest(OrderPreviewRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /**
     * 방송 중 화면(FL_S_LV_STREAM)의 주문 건수·매출 지표. 판매자(방송 소유자) 전용이며,
     * 소유권은 live-service가 주는 sellerId와 로그인 사용자를 대조해 판정한다.
     *
     * <p>{@code pending}(미결제)을 같이 내리는 이유: 방송 중 주문은 대부분 아직 PENDING이라
     * 결제완료만 세면 방송 내내 0에 가깝게 보인다. 표시 방식은 FE가 고른다.
     */
    @GetMapping("/live-stats")
    public LiveOrderStatsResponse liveStats(@LoginUser CurrentUser user, @RequestParam UUID liveId) {
        return LiveOrderStatsResponse.from(liveId, liveOrderStatsService.getStats(user.id(), liveId));
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

    private List<OrderLineItemRequest> toLineItems(List<OrderLineItemRequestDto> dtos) {
        return dtos.stream().map(OrderLineItemRequestDto::toApplication).toList();
    }
}
