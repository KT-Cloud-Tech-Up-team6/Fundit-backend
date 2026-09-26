package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.ReturnPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * R05(후반부) — 환불 신청 전 실제 환불액을 서버가 미리 계산해 보여준다. FE가 임의로 계산한 값을
 * 실제 환불액으로 고지하지 않기 위함이다.
 *
 * <p>신청 단위(주문 전체/개별 리워드)는 아직 정책 미확정이라 이 메서드는 현재 지원되는 단위
 * (펀딩=주문 전체)만 다룬다. 금액 산정은 환불 정책 V.1.0 「취소·반품·교환 공통 정책」을 따른다 —
 * 구매자 귀책 반품은 반품 배송비를 차감하고, 판매자 귀책은 전액이며, 귀책이 불분명한 건(사유
 * "기타")은 검토 결과에 따라 달라지므로 확정액을 내려보내지 않는다({@code refundAmount = null}).
 */
@Service
@RequiredArgsConstructor
public class RefundEstimateService {

    private final PaymentRepository paymentRepository;
    private final OrderFundingClient orderFundingClient;

    /**
     * @param triggerType       신청하려는 환불 유형(미지정이면 전액 기준으로 계산)
     * @param faultUndetermined 사유가 "기타"이거나 귀책이 불분명한 건 — 확정액을 표시하지 않는다
     */
    @Transactional(readOnly = true)
    public RefundEstimate estimate(UUID accountId, UUID orderId, RefundTriggerType triggerType,
                                    boolean faultUndetermined) {
        Payment payment = paymentRepository.findCompletedByFundingId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(orderId);
        long rewardAmount = payment.getAmount() - snapshot.shippingFee() + snapshot.discountAmount();
        boolean buyerFaultReturn = triggerType == RefundTriggerType.RETURN_CHANGE_OF_MIND;
        long returnShippingFee = buyerFaultReturn ? ReturnPolicy.RETURN_SHIPPING_FEE : 0L;
        Long refundAmount = faultUndetermined ? null : payment.getAmount() - returnShippingFee;
        return new RefundEstimate(orderId, rewardAmount, snapshot.shippingFee(), snapshot.discountAmount(),
                returnShippingFee, refundAmount);
    }

    /** {@code refundAmount}는 귀책이 불분명한 건에서 null이다(확정액으로 표시하면 안 된다). */
    public record RefundEstimate(UUID orderId, long rewardAmount, long shippingFee, long discountAmount,
                                  long returnShippingFee, Long refundAmount) {
    }
}
