package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * R05(후반부) — 환불 신청 전 실제 환불액을 서버가 미리 계산해 보여준다. FE가 임의로 계산한 값을
 * 실제 환불액으로 고지하지 않기 위함이다.
 *
 * <p>신청 단위(주문 전체/개별 리워드)는 아직 정책 미확정이라 이 메서드는 현재 지원되는 단위
 * (펀딩=주문 전체)만 다룬다. {@code refundAmount}는 오늘 기준 항상 {@code payment.amount}와
 * 같다 — 반품비 차감(R04) 정책이 아직 없어 모든 환불 트리거가 전액 환불만 실행하기 때문이다
 * (DefectRefundDecisionService 참고). R04가 확정되면 이 메서드만 고치면 된다 — 응답 계약은
 * 그대로 두고 {@code refundAmount} 계산식만 반품비 차감을 반영하도록 바뀐다.
 */
@Service
@RequiredArgsConstructor
public class RefundEstimateService {

    private final PaymentRepository paymentRepository;
    private final OrderFundingClient orderFundingClient;

    @Transactional(readOnly = true)
    public RefundEstimate estimate(UUID accountId, UUID orderId) {
        Payment payment = paymentRepository.findCompletedByFundingId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(orderId);
        long rewardAmount = payment.getAmount() - snapshot.shippingFee() + snapshot.discountAmount();
        return new RefundEstimate(orderId, rewardAmount, snapshot.shippingFee(), snapshot.discountAmount(),
                payment.getAmount());
    }

    public record RefundEstimate(UUID orderId, long rewardAmount, long shippingFee, long discountAmount,
                                  long refundAmount) {
    }
}
