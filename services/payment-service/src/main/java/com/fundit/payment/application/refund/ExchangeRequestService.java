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

/**
 * 교환 신청 — 판매자 검토 대기(REQUESTED)로만 접수한다. 하자환불(PAYMENT-006)과 동일한 신청
 * 골격(증빙 필수, seller_id 비정규화)을 재사용하되, 승인/완료는 이번 범위에 없다 — 교환은
 * 결제취소가 아니라 재발송이 필요해 fulfillment-service 연동 설계가 먼저 필요하다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRequestService {

    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final OrderFundingClient orderFundingClient;

    @Transactional
    public ExchangeRequestResult request(UUID accountId, UUID fundingId, String reasonDetail,
                                          List<String> evidenceUrls) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        UUID sellerId = orderFundingClient.fetch(fundingId).sellerId();
        RefundRequest saved = refundRequestRepository.save(
                RefundRequest.requestExchange(fundingId, payment.getId(), sellerId, reasonDetail, evidenceUrls));
        return new ExchangeRequestResult(saved.getId(), saved.getStatus().name());
    }

    public record ExchangeRequestResult(Long refundId, String status) {
    }
}
