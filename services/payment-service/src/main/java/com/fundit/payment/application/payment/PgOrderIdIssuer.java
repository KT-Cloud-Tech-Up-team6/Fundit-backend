package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 중복되지 않는 {@code pg_order_id}를 채번한다. 리워드 결제(PAYMENT-001)와 교환 배송비 결제가
 * 같은 유니크 제약({@code uq_payments_pg_order_id})을 공유하므로 채번 규칙도 한곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class PgOrderIdIssuer {

    private final PaymentRepository paymentRepository;

    public String issue() {
        String candidate;
        do {
            candidate = PgOrderIdGenerator.generate();
        } while (paymentRepository.existsByPgOrderId(candidate));
        return candidate;
    }
}
