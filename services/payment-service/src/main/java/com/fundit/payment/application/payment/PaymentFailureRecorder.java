package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 실패(FAILED) 기록 전용 — {@link PaymentConfirmService#confirm}의 트랜잭션과 분리된
 * 별도 트랜잭션(REQUIRES_NEW)에서 커밋한다. confirm()이 토스 승인 실패 후 BusinessException을
 * 던지면 그 트랜잭션 전체가 롤백되는데, 같은 트랜잭션에서 markFailed()를 저장하면 FAILED 기록까지
 * 함께 사라져 PENDING으로 남는다 — 그러면 PaymentCreateService의 PENDING 재사용 로직이 같은
 * pgOrderId를 계속 돌려주게 된다. Self-invocation으로는 @Transactional 프록시가 가로채지 못해
 * 별도 빈으로 분리했다.
 */
@Service
@RequiredArgsConstructor
public class PaymentFailureRecorder {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Payment payment) {
        payment.markFailed();
        paymentRepository.save(payment);
    }
}
