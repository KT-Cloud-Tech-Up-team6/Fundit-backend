package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.ReturnPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 발송 후(배송 완료 후) 환불·교환 신청 접수 — 하자환불(PAYMENT-006, DEFECT), 교환(EXCHANGE),
 * 구매자 귀책 반품(RETURN_CHANGE_OF_MIND, 환불 정책 V.1.0). 세 유형이 접수 검증과 저장 골격을
 * 전부 공유하므로(소유권 → 수령 후 7일 → 중복 신청) 한 서비스에 모았다.
 *
 * <p>접수 시점에는 결제를 취소하지 않는다 — 하자환불은 판매자 귀책 확인이, 반품은 "회수 후
 * 환불"이 선행되어야 하므로 판매자 결정(PAYMENT-007)에서 실행한다.
 */
@Service
@RequiredArgsConstructor
public class PostShipmentRefundRequestService {

    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final OrderFundingClient orderFundingClient;
    private final ShippingStatusClient shippingStatusClient;

    @Transactional
    public PostShipmentRefundRequestResult request(UUID accountId, UUID fundingId, RefundTriggerType triggerType,
                                                    String reasonDetail, List<String> evidenceUrls) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        assertWithinRequestWindow(fundingId);
        if (refundRequestRepository.existsUnresolvedPostShipmentRequest(fundingId)) {
            throw new BusinessException(PaymentErrorCode.REFUND_ALREADY_REQUESTED);
        }
        if (triggerType == RefundTriggerType.RETURN_CHANGE_OF_MIND
                && !ReturnPolicy.coversReturnFee(payment.getAmount())) {
            throw new BusinessException(PaymentErrorCode.RETURN_FEE_EXCEEDS_AMOUNT);
        }

        // 판매자 환불 목록 조회(PAYMENT-003 seller 변형)에 쓰기 위해 신청 시점에 미리 조회해둔다 —
        // 목록 조회 때마다 건별로 order-service를 호출하지 않기 위한 비정규화.
        UUID sellerId = orderFundingClient.fetch(fundingId).sellerId();

        RefundRequest saved = refundRequestRepository.save(RefundRequest.requestAfterShipment(triggerType, fundingId,
                payment.getId(), sellerId, reasonDetail, evidenceUrls));
        long returnShippingFee = triggerType == RefundTriggerType.RETURN_CHANGE_OF_MIND
                ? ReturnPolicy.RETURN_SHIPPING_FEE : 0L;
        return new PostShipmentRefundRequestResult(saved.getId(), saved.getStatus().name(), payment.getAmount(),
                returnShippingFee, payment.getAmount() - returnShippingFee);
    }

    /**
     * 환불 정책 V.1.0 "수령 후 7일 이내 신청" — 기준일은 배송 완료(deliveredAt)다. 배송 완료 전
     * 반품·교환은 접수 대상이 아니다(발송 전이라면 발송지연 취소 경로가 따로 있다).
     */
    private void assertWithinRequestWindow(UUID fundingId) {
        Instant deliveredAt = shippingStatusClient.fetch(fundingId).deliveredAt();
        if (deliveredAt == null) {
            throw new BusinessException(PaymentErrorCode.NOT_DELIVERED);
        }
        if (!ReturnPolicy.isWithinRequestWindow(deliveredAt, Instant.now())) {
            throw new BusinessException(PaymentErrorCode.RETURN_PERIOD_EXPIRED);
        }
    }

    /** {@code returnShippingFee}/{@code estimatedRefundAmount}는 반품이 아니면 0/전액이다. */
    public record PostShipmentRefundRequestResult(Long refundId, String status, long paymentAmount,
                                                   long returnShippingFee, long estimatedRefundAmount) {
    }
}
