package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** PAYMENT-006 — 하자환불 신청. 아직 결제 취소를 실행하지 않는다(판매자 승인 대기, PAYMENT-007에서 실행). */
@Service
@RequiredArgsConstructor
public class DefectRefundRequestService {

    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final OrderFundingClient orderFundingClient;

    @Transactional
    public DefectRefundRequestResult request(UUID accountId, UUID fundingId, String reasonDetail,
                                               List<String> evidenceUrls) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 판매자 환불 목록 조회(PAYMENT-003 seller 변형)에 쓰기 위해 신청 시점에 미리 조회해둔다 —
        // 목록 조회 때마다 건별로 order-service를 호출하지 않기 위한 비정규화.
        UUID sellerId = orderFundingClient.fetch(fundingId).sellerId();

        RefundRequest saved = refundRequestRepository.save(
                RefundRequest.requestDefect(fundingId, payment.getId(), sellerId, reasonDetail, evidenceUrls));
        return new DefectRefundRequestResult(saved.getId(), saved.getStatus().name());
    }

    public record DefectRefundRequestResult(Long refundId, String status) {
    }
}
