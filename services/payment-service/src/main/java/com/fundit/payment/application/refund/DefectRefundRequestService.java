package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
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

    @Transactional
    public DefectRefundRequestResult request(UUID accountId, Long fundingId, String reasonDetail,
                                               List<String> evidenceUrls) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        RefundRequest saved = refundRequestRepository.save(
                RefundRequest.requestDefect(fundingId, payment.getId(), reasonDetail, evidenceUrls));
        return new DefectRefundRequestResult(saved.getId(), saved.getStatus().name());
    }

    public record DefectRefundRequestResult(Long refundId, String status) {
    }
}
