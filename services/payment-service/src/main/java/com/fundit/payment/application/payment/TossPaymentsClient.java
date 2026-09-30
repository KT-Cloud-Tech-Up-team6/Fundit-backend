package com.fundit.payment.application.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
     * 결제 단건 조회({@code GET /v1/payments/{paymentKey}}). 승인 결과가 불명확할 때(5xx·타임아웃 뒤 재확정,
     * 이미 처리된 결제라는 응답) 토스에 실제로 승인됐는지 대조하는 데만 쓴다.
     */
    TossPaymentLookup lookup(String paymentKey);

    /**
     * 주문번호로 결제 조회({@code GET /v1/payments/orders/{orderId}}). 결제 키가 아직 없는 대기 결제가 토스에서
     * 실제로 승인됐는지 확인할 때 쓴다. 토스에 결제가 없으면(인증 전) 빈 값이다.
     */
    Optional<TossPaymentLookup> lookupByOrderId(String orderId);

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

    /**
     * @param status  토스 결제 상태(승인 완료는 {@code DONE})
     * @param cancels 이 결제의 취소 이력(없으면 빈 목록). 취소 결과가 불명확할 때 어떤 취소가 실제로 일어났는지
     *                transactionKey로 대조하는 데 쓴다
     */
    record TossPaymentLookup(String status, TossPaymentResult payment, List<TossCancelResult> cancels) {

        public boolean isDone() {
            return "DONE".equals(status);
        }

        /**
         * 이 결제가 앞으로도 승인될 수 없는 상태인지 — 인증 전(READY)이거나 끝난 실패(ABORTED·EXPIRED).
         * IN_PROGRESS(인증 완료, 승인 대기)는 우리 승인 호출이 진행 중일 수 있어 여기에 넣지 않는다.
         */
        public boolean isNeverApprovable() {
            return "READY".equals(status) || "ABORTED".equals(status) || "EXPIRED".equals(status);
        }
    }
}
