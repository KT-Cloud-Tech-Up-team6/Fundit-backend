package com.fundit.payment.application.payment;

import java.time.Instant;

/**
 * 토스페이먼츠 결제위젯 서버-투-서버 연동 아웃바운드 포트(승인/취소).
 * 반드시 "위젯 전용 시크릿 키"로 호출한다("API 개별연동 키"와는 다른 키 페어 — PaymentERD.md 1장,
 * payment-service CLAUDE.md "Toss 연동" 참고).
 *
 * <p>구현체는 Toss가 4xx로 응답한 "비즈니스 실패"(카드 한도 초과, 세션 만료 등)를
 * {@link TossApiException}으로 던지고, 네트워크 오류·5xx 등 "의존성 자체의 장애"는
 * {@link com.fundit.common.error.DependencyFailureException}으로 던진다(error-handling.md 구분 원칙).
 */
public interface TossPaymentsClient {

    TossPaymentResult confirm(String paymentKey, String orderId, long amount);

    TossCancelResult cancel(String paymentKey, long cancelAmount, String cancelReason);

    /**
     * @param secret 웹훅 유효성 검증용 값(Payment 객체 secret 필드). 응답에 항상 포함되는지는
     *               문서상 명확하지 않아 null일 수 있다[가정] — null이면 웹훅 서명 검증을
     *               건너뛰지 않고 별도 처리(웹훅 서비스 참고)한다.
     */
    record TossPaymentResult(String paymentKey, String orderId, String secret, String method,
                              String easyPayProvider, Instant approvedAt, long totalAmount) {
    }

    record TossCancelResult(String transactionKey, Instant canceledAt, long cancelAmount) {
    }
}
