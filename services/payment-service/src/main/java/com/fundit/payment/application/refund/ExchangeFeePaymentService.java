package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.payment.PgOrderIdIssuer;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundReasonTag;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 구매자 귀책 교환의 교환 배송비 결제 시도 생성. 승인(confirm)은 리워드 결제와 같은
 * {@code POST /api/v2/payments/confirm}을 쓰고, 용도 분기는 {@code PaymentConfirmService}가 한다.
 *
 * <p>금액은 order-service 스냅샷이 아니라 정책 상수({@code ReturnPolicy.EXCHANGE_SHIPPING_FEE})다 —
 * 주문 금액과 무관한 별도 결제이기 때문이다. 그래도 "금액을 재계산하지 않는다"는 원칙은 유지된다:
 * 승인 시 대조하는 값은 이 시점에 고정 저장된 {@code payments.amount}다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeFeePaymentService {

    private static final String ORDER_NAME = "교환 배송비";

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PgOrderIdIssuer pgOrderIdIssuer;

    @Transactional
    public ExchangeFeePaymentResult create(UUID accountId, Long refundId) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (refundRequest.getTriggerType() != RefundTriggerType.EXCHANGE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "교환 신청이 아닙니다.");
        }

        // 소유권(S4) — 교환 신청에는 회원 정보가 없어 원 결제로 확인한다.
        Payment rewardPayment = paymentRepository.findById(refundRequest.getPaymentId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!rewardPayment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (refundRequest.getStatus() != RefundRequestStatus.APPROVED) {
            throw new BusinessException(PaymentErrorCode.EXCHANGE_NOT_APPROVED);
        }

        long amount = ExchangeReason.orOther(RefundReasonTag.parse(refundRequest.getReasonDetail()).reasonType())
                .additionalPaymentAmount();
        if (amount <= 0) {
            throw new BusinessException(PaymentErrorCode.EXCHANGE_FEE_NOT_REQUIRED);
        }

        Optional<Payment> existing = paymentRepository.findLatestExchangeFeeByRefundRequestId(refundId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.isCompleted()) {
                throw new BusinessException(PaymentErrorCode.EXCHANGE_FEE_ALREADY_PAID);
            }
            // 이미 pg_order_id가 발급된 시도가 있으면 재사용한다(PAYMENT-001과 같은 규칙).
            if (payment.isPending()) {
                return ExchangeFeePaymentResult.from(payment);
            }
        }

        Payment payment = Payment.createExchangeFee(refundRequest.getFundingId(), accountId, pgOrderIdIssuer.issue(),
                amount, ORDER_NAME, refundId, UUID.randomUUID().toString());
        return ExchangeFeePaymentResult.from(paymentRepository.save(payment));
    }

    /** 결제위젯 렌더링에 필요한 값 — 리워드 결제 생성(PAYMENT-001) 응답과 같은 형태다. */
    public record ExchangeFeePaymentResult(UUID paymentId, String pgOrderId, long amount, String orderName) {
        static ExchangeFeePaymentResult from(Payment payment) {
            return new ExchangeFeePaymentResult(payment.getId(), payment.getPgOrderId(), payment.getAmount(),
                    payment.getOrderName());
        }
    }
}
