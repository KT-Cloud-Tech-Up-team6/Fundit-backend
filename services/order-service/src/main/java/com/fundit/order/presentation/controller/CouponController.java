package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.coupon.CouponBoxQueryService;
import com.fundit.order.application.coupon.CouponIssuanceService;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.infrastructure.security.CurrentMember;
import com.fundit.order.presentation.dto.CouponBoxItemResponse;
import com.fundit.order.presentation.dto.CouponClaimResponse;
import com.fundit.order.presentation.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponIssuanceService couponIssuanceService;
    private final CouponBoxQueryService couponBoxQueryService;
    private final CouponRepository couponRepository;

    /** ORDER-012 — 쿠폰 발급받기(소비자 능동 클레임). */
    @PostMapping("/{couponCode}/claim")
    public CouponClaimResponse claim(@CurrentMember UUID memberId, @PathVariable String couponCode) {
        CouponIssuance issuance = couponIssuanceService.claim(memberId, couponCode);
        Coupon coupon = couponRepository.findByCouponCode(couponCode)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return CouponClaimResponse.from(issuance, coupon);
    }

    /** ORDER-009 — 쿠폰함 조회. */
    @GetMapping("/me")
    public PageResponse<CouponBoxItemResponse> myCoupons(@CurrentMember UUID memberId,
                                                           @RequestParam(required = false) CouponIssuanceStatus status,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(couponBoxQueryService.list(memberId, status, pageable)
                .map(CouponBoxItemResponse::from));
    }
}
