package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.Payment;

/**
 * 교환 배송비 결제가 승인됐을 때 교환 흐름을 이어받는 포트. 결제 승인(PAYMENT-002)은 용도만
 * 구분해 이 포트로 넘기고, 교환 신청 상태 전이·재발송 요청은 refund 쪽 구현체가 담당한다
 * (application.payment → application.refund 역방향 의존을 만들지 않기 위한 포트).
 */
public interface ExchangeFeePaymentListener {

    void onExchangeFeePaid(Payment payment);
}
