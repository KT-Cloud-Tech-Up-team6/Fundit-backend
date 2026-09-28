package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.DefectType;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.ReturnPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * R05(후반부) — 환불·교환 신청 전 실제 금액을 서버가 미리 계산해 보여준다. FE가 임의로 계산한
 * 값을 고지하지 않기 위함이라 결제 금액·차감(반품비)·가산(교환비)·확정 여부를 모두 내려보낸다.
 *
 * <p>신청 단위(주문 전체/개별 리워드)는 아직 정책 미확정이라 이 메서드는 현재 지원되는 단위
 * (펀딩=주문 전체)만 다룬다. 금액 산정은 환불 정책 V.1.0 「취소·반품·교환 공통 정책」을 따른다 —
 * 구매자 귀책 반품은 반품 배송비를 차감하고, 구매자 귀책 교환은 교환 배송비를 별도 결제하며,
 * 판매자 귀책·기타처럼 검토로 확정되는 건은 {@code confirmed=false}로 "확인 시"임을 알린다.
 */
@Service
@RequiredArgsConstructor
public class RefundEstimateService {

    private final PaymentRepository paymentRepository;
    private final OrderFundingClient orderFundingClient;

    /**
     * @param triggerType    신청하려는 유형(미지정이면 전액 환불 기준으로 계산)
     * @param defectType     하자환불 사유(하자환불일 때만 의미가 있다)
     * @param exchangeReason 교환 사유(교환일 때만 의미가 있다) — 미지정이면 기타로 본다
     */
    @Transactional(readOnly = true)
    public RefundEstimate estimate(UUID accountId, UUID orderId, RefundTriggerType triggerType,
                                    DefectType defectType, ExchangeReason exchangeReason) {
        Payment payment = paymentRepository.findCompletedByFundingId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(orderId);
        long paymentAmount = payment.getAmount();
        long rewardAmount = paymentAmount - snapshot.shippingFee() + snapshot.discountAmount();
        ExchangeReason exchange = exchangeReason == null ? ExchangeReason.OTHER : exchangeReason;
        long returnShippingFee = triggerType == RefundTriggerType.RETURN_CHANGE_OF_MIND
                ? ReturnPolicy.RETURN_SHIPPING_FEE : 0L;
        long additionalPaymentAmount = triggerType == RefundTriggerType.EXCHANGE
                ? exchange.additionalPaymentAmount() : 0L;
        return new RefundEstimate(orderId, paymentAmount, rewardAmount, snapshot.shippingFee(),
                snapshot.discountAmount(), returnShippingFee, additionalPaymentAmount,
                refundAmount(triggerType, defectType, paymentAmount, returnShippingFee),
                isAmountConfirmed(triggerType, exchange));
    }

    /**
     * 교환은 결제취소를 수반하지 않아 환불액이 없고(null), 귀책이 불분명한 하자환불(사유 "기타")도
     * 검토 결과에 따라 달라져 확정액을 내려보내지 않는다 — 화면이 확정액으로 표시하면 안 된다.
     */
    private Long refundAmount(RefundTriggerType triggerType, DefectType defectType, long paymentAmount,
                               long returnShippingFee) {
        if (triggerType == RefundTriggerType.EXCHANGE) {
            return null;
        }
        if (triggerType == RefundTriggerType.DEFECT && defectType == DefectType.OTHER) {
            return null;
        }
        return paymentAmount - returnShippingFee;
    }

    /**
     * 금액이 정책으로 확정되는 건만 true다. 하자환불 전체와 판매자 귀책·기타 교환은 판매자 검토로
     * 귀책이 정해져 금액이 바뀔 수 있어(정책 표기 "23,000원(확인 시)") false로 내려보낸다.
     */
    private boolean isAmountConfirmed(RefundTriggerType triggerType, ExchangeReason exchangeReason) {
        if (triggerType == RefundTriggerType.DEFECT) {
            return false;
        }
        if (triggerType == RefundTriggerType.EXCHANGE) {
            return exchangeReason.isAmountConfirmed();
        }
        return true;
    }

    /**
     * {@code refundAmount}는 교환과 귀책이 불분명한 건에서 null이고, {@code confirmed=false}면
     * 화면이 확정액으로 표시하면 안 된다(금액이 있어도 "확인 시"다).
     */
    public record RefundEstimate(UUID orderId, long paymentAmount, long rewardAmount, long shippingFee,
                                  long discountAmount, long returnShippingFee, long additionalPaymentAmount,
                                  Long refundAmount, boolean confirmed) {
    }
}
