package com.fundit.order.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.presentation.dto.SellerOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 판매자 발송 목록 — fulfillment-service 발송정보 등록 화면이 발송 대상을 조회하는 데 쓴다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/projects/{projectId}/orders")
    public List<SellerOrderResponse> listForSeller(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        return orderQueryService.listForSeller(user.id(), projectId).stream()
                .map(SellerOrderResponse::from)
                .toList();
    }
}
