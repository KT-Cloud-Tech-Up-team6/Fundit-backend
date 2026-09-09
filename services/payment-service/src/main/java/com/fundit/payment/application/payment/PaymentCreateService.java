package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** PAYMENT-001 — 결제 시도 생성(결제위젯 렌더링 준비). */
@Service
@RequiredArgsConstructor
public class PaymentCreateService {

    private final OrderFundingClient orderFundingClient;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentCreateResult create(UUID accountId, Long fundingId) {
        // 예외 처리 — 이미 pg_order_id가 발급된 결제 시도가 있으면 재사용(중복 생성 방지, PAYMENT-001 예외 처리 항목)
        var existing = paymentRepository.findPendingByFundingId(fundingId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.isOwnedBy(accountId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            return PaymentCreateResult.from(payment);
        }

        // ① order-service 내부 API 동기 호출
        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);

        // ② 소유권 검증
        if (!snapshot.memberId().equals(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        // ③ 상태 검증
        if (!snapshot.isPending()) {
            throw new BusinessException(PaymentErrorCode.FUNDING_NOT_PENDING);
        }

        // ④ Payment(PENDING) 생성 — finalAmount/orderName/couponIssuanceId를 스냅샷으로 고정
        Payment payment = Payment.create(fundingId, accountId, generateUniquePgOrderId(),
                snapshot.finalAmount(), snapshot.orderName(), snapshot.couponIssuanceId(),
                UUID.randomUUID().toString());
        return PaymentCreateResult.from(paymentRepository.save(payment));
    }

    private String generateUniquePgOrderId() {
        String candidate;
        do {
            candidate = PgOrderIdGenerator.generate();
        } while (paymentRepository.existsByPgOrderId(candidate));
        return candidate;
    }

    public record PaymentCreateResult(UUID paymentId, String pgOrderId, long amount, String orderName) {
        static PaymentCreateResult from(Payment payment) {
            return new PaymentCreateResult(payment.getId(), payment.getPgOrderId(), payment.getAmount(),
                    payment.getOrderName());
        }
    }
}
