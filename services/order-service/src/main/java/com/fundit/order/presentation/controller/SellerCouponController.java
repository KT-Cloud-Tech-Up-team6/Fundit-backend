package com.fundit.order.presentation.controller;

import com.fundit.order.application.coupon.MakerCouponIssueService;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.infrastructure.security.CurrentMember;
import com.fundit.order.presentation.dto.MakerCouponIssueRequest;
import com.fundit.order.presentation.dto.MakerCouponIssueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** ORDER-008 — 쿠폰 발급(메이커). */
@RestController
@RequestMapping("/api/v1/sellers/coupons")
@RequiredArgsConstructor
public class SellerCouponController {

    private final MakerCouponIssueService makerCouponIssueService;

    @PostMapping
    public ResponseEntity<MakerCouponIssueResponse> issue(@CurrentMember UUID sellerId,
                                                            @Valid @RequestBody MakerCouponIssueRequest request) {
        Coupon coupon = makerCouponIssueService.issue(sellerId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(MakerCouponIssueResponse.from(coupon));
    }
}
