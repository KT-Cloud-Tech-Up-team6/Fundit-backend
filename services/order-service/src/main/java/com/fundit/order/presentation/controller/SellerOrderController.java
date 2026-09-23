package com.fundit.order.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.ShippingFilter;
import com.fundit.order.presentation.dto.PageResponse;
import com.fundit.order.presentation.dto.SellerOrderResponse;
import com.fundit.order.presentation.dto.SellerOrderShippingCountsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 판매자 발송 목록 — fulfillment-service 발송정보 등록 화면이 발송 대상을 조회하는 데 쓴다.
 * #129 — 검색어(주문번호·서포터명)·발송상태 필터·상태별 건수·페이지네이션.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/projects/{projectId}/orders")
    public PageResponse<SellerOrderResponse> listForSeller(@LoginUser CurrentUser user, @PathVariable UUID projectId,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(required = false) ShippingFilter shippingFilter,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderQueryService.listForSeller(user.id(), projectId, shippingFilter, q, pageable)
                .map(SellerOrderResponse::from));
    }

    @GetMapping("/projects/{projectId}/orders/shipping-status-counts")
    public SellerOrderShippingCountsResponse shippingStatusCounts(@LoginUser CurrentUser user,
                                                                    @PathVariable UUID projectId) {
        var counts = orderQueryService.sellerOrderShippingCounts(user.id(), projectId);
        return new SellerOrderShippingCountsResponse(counts.waiting(), counts.shipped());
    }
}
