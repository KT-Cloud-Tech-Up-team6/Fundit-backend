package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.payment.PaymentConfirmService;
import com.fundit.payment.application.payment.PaymentCreateService;
import com.fundit.payment.application.payment.TossWebhookService;
import com.fundit.payment.presentation.dto.PaymentConfirmRequest;
import com.fundit.payment.presentation.dto.PaymentConfirmResponse;
import com.fundit.payment.presentation.dto.PaymentCreateRequest;
import com.fundit.payment.presentation.dto.PaymentCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCreateService paymentCreateService;
    private final PaymentConfirmService paymentConfirmService;
    private final TossWebhookService tossWebhookService;

    /** PAYMENT-001 — 결제 시도 생성(결제위젯 렌더링 준비). */
    @PostMapping
    public ResponseEntity<PaymentCreateResponse> create(@LoginUser CurrentUser user,
                                                          @Valid @RequestBody PaymentCreateRequest request) {
        var result = paymentCreateService.create(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentCreateResponse.from(result));
    }

    /** PAYMENT-002 — 결제 승인 처리. */
    @PostMapping("/confirm")
    public PaymentConfirmResponse confirm(@LoginUser CurrentUser user,
                                           @Valid @RequestBody PaymentConfirmRequest request) {
        var result = paymentConfirmService.confirm(user.id(), request.paymentKey(), request.orderId(), request.amount());
        return PaymentConfirmResponse.from(result);
    }

    /**
     * 토스 웹훅 수신(보조). 로그인 인증을 걸지 않는다 — 토스 서버가 직접 호출하며,
     * {@code X-User-Id} 헤더가 없는 요청이라 {@code InternalGatewaySecretFilter}가 자동으로
     * 통과시킨다(payment-service CLAUDE.md "인증" 참고). 서명(secret) 검증은
     * {@link TossWebhookService}가 수행한다. 토스가 보내는 필드가 문서화보다 많고 이벤트
     * 타입별로 달라 엄격한 DTO 대신 {@code Map}으로 느슨하게 받는다.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/webhook/toss")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        String eventType = (String) payload.get("eventType");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String paymentKey = data == null ? null : (String) data.get("paymentKey");
        String status = data == null ? null : (String) data.get("status");
        String secret = data == null ? null : (String) data.get("secret");
        tossWebhookService.handle(eventType, paymentKey, status, secret);
        return ResponseEntity.ok().build();
    }
}
