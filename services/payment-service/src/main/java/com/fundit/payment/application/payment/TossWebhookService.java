package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * 토스 웹훅(PAYMENT_STATUS_CHANGED/CANCEL_STATUS_CHANGED 등) 수신 — 1-2 승인 흐름을 대체하지
 * 않는 보조 상태 통지다(PaymentApiSpec.md 1-3).
 *
 * <p>서명 검증은 토스가 결제 승인 응답에 실어주는 {@code secret} 값을 승인 시점에 저장해뒀다가
 * 웹훅 payload의 {@code data.secret}과 상수 시간 비교로 대조하는 방식이다
 * (https://docs.tosspayments.com/reference/using-api/webhook-events, V1 마이그레이션 코멘트 참고).
 * 아직 승인되지 않은(따라서 secret이 없는) 결제에 대한 웹훅은 검증할 방법이 없어 거부한다.
 */
@Service
@RequiredArgsConstructor
public class TossWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TossWebhookService.class);

    private final PaymentRepository paymentRepository;

    public void handle(String eventType, String paymentKey, String status, String secret) {
        if (paymentKey == null || secret == null) {
            throw new BusinessException(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        Optional<Payment> maybePayment = paymentRepository.findByPgPaymentKey(paymentKey);
        if (maybePayment.isEmpty()) {
            // 이 서비스가 모르는 paymentKey — 위조/오발송일 수 있으나, 존재 자체를 노출하지 않기 위해
            // 200으로 조용히 무시한다(재시도 폭주 방지, 토스는 2xx가 아니면 최대 7회 재시도).
            log.warn("웹훅 대상 결제를 찾을 수 없습니다. paymentKey={}", paymentKey);
            return;
        }

        Payment payment = maybePayment.get();
        if (payment.getPgSecret() == null || !constantTimeEquals(secret, payment.getPgSecret())) {
            throw new BusinessException(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        // 현재는 1-2(API 호출) 흐름이 상태 반영의 주 경로이므로 웹훅은 감사 로그만 남긴다.
        // 가상계좌(DEPOSIT_CALLBACK) 지원이 확정되면 이 지점에 입금 완료 처리가 필수로 추가되어야
        // 한다(PaymentERD.md 6장 — 이번 구현 범위 밖).
        log.info("토스 웹훅 검증 완료. eventType={} paymentKey={} status={}", eventType, paymentKey, status);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
