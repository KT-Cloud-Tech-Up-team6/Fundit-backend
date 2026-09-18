package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.payment.PaymentConfirmService;
import com.fundit.payment.application.payment.PaymentCreateService;
import com.fundit.payment.presentation.dto.PaymentConfirmRequest;
import com.fundit.payment.presentation.dto.PaymentConfirmResponseV2;
import com.fundit.payment.presentation.dto.PaymentCreateRequestV2;
import com.fundit.payment.presentation.dto.PaymentCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAYMENT-001/002 v2 — cross-service ID 통일(#69). fundingId를 order-service의
 * publicId(UUID)로 그대로 받는다/돌려준다 — v1({@link PaymentController})처럼 내부 해석 호출이
 * 없다. 웹훅은 paymentKey만 쓰므로 v1과 동일 계약이라 {@link PaymentController}에 둔다.
 */
@RestController
@RequestMapping("/api/v2/payments")
@RequiredArgsConstructor
public class PaymentControllerV2 {

    private final PaymentCreateService paymentCreateService;
    private final PaymentConfirmService paymentConfirmService;

    @PostMapping
    public ResponseEntity<PaymentCreateResponse> create(@LoginUser CurrentUser user,
                                                          @Valid @RequestBody PaymentCreateRequestV2 request) {
        var result = paymentCreateService.create(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentCreateResponse.from(result));
    }

    @PostMapping("/confirm")
    public PaymentConfirmResponseV2 confirm(@LoginUser CurrentUser user,
                                             @Valid @RequestBody PaymentConfirmRequest request) {
        var result = paymentConfirmService.confirm(user.id(), request.paymentKey(), request.orderId(), request.amount());
        return PaymentConfirmResponseV2.from(result);
    }
}
