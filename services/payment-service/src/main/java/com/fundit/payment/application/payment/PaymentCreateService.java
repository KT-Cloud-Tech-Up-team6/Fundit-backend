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

import java.util.List;
import java.util.UUID;

/** PAYMENT-001 — 결제 시도 생성(결제위젯 렌더링 준비). */
@Service
@RequiredArgsConstructor
public class PaymentCreateService {

    private final OrderFundingClient orderFundingClient;
    private final PaymentRepository paymentRepository;
    private final PgOrderIdIssuer pgOrderIdIssuer;

    @Transactional
    public PaymentCreateResult create(UUID accountId, UUID orderId) {
        // 이중 결제 방지 — order-service의 결제완료 반영(FundingStatus 전이)이 비동기라 그 짧은 창 동안
        // 재호출되면 이미 COMPLETED된 펀딩에도 새 PENDING 결제가 또 생성될 수 있다. 그 창을 없애기 위해
        // completed_funding_id 유니크 인덱스(최종 방어선)보다 앞서 여기서 먼저 막는다.
        var completed = paymentRepository.findCompletedByFundingId(orderId);
        if (completed.isPresent()) {
            if (!completed.get().isOwnedBy(accountId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 결제가 완료된 주문입니다.");
        }

        // 예외 처리 — 이미 pg_order_id가 발급된 결제 시도가 있으면 재사용(중복 생성 방지, PAYMENT-001 예외 처리 항목)
        var existing = paymentRepository.findPendingByFundingId(orderId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.isOwnedBy(accountId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            return PaymentCreateResult.from(payment);
        }

        // ① order-service 내부 API 동기 호출
        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(orderId);

        // ② 소유권 검증
        if (!snapshot.memberId().equals(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        // ③ 상태 검증
        if (!snapshot.isPending()) {
            throw new BusinessException(PaymentErrorCode.FUNDING_NOT_PENDING);
        }

        // ④ Payment(PENDING) 생성 — finalAmount/orderName/couponIssuanceIds를 스냅샷으로 고정
        Payment payment = Payment.create(orderId, accountId, pgOrderIdIssuer.issue(),
                snapshot.finalAmount(), snapshot.orderName(), snapshot.couponIssuanceIds(),
                UUID.randomUUID().toString());
        return PaymentCreateResult.from(paymentRepository.save(payment));
    }

    public record PaymentCreateResult(UUID paymentId, String pgOrderId, long amount, String orderName,
                                       List<Long> couponIssuanceIds) {
        static PaymentCreateResult from(Payment payment) {
            return new PaymentCreateResult(payment.getId(), payment.getPgOrderId(), payment.getAmount(),
                    payment.getOrderName(), payment.getCouponIssuanceIds());
        }
    }
}
