package com.fundit.order.application.payment;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentEventSyncService implements PaymentEventListener {

    private final FundingRepository fundingRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        fundingRepository.findById(event.fundingId()).ifPresent(funding -> {
            funding.markPaymentCompleted();
            fundingRepository.save(funding);
        });

        if (event.couponIssuanceId() != null) {
            couponIssuanceRepository.findById(event.couponIssuanceId()).ifPresent(issuance -> {
                if (issuance.isAvailable()) {
                    issuance.markUsed(event.fundingId());
                    couponIssuanceRepository.save(issuance);
                }
            });
        }
    }

    @Override
    @Transactional
    public void onRefundCompleted(RefundCompletedEvent event) {
        boolean shouldRestoreCoupon = switch (event.refundReason()) {
            case GOAL_FAILURE_AUTO_REFUND -> true;
            case CANCELLED_BY_MEMBER -> false;
            case POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY -> event.fullRefund();
        };

        if (shouldRestoreCoupon && event.couponIssuanceId() != null) {
            couponIssuanceRepository.findById(event.couponIssuanceId())
                    .ifPresent(this::restore);
        }

        boolean isPostSuccessRefund = event.refundReason() == RefundReason.POST_SUCCESS_DEFECT
                || event.refundReason() == RefundReason.POST_SUCCESS_DELAY;
        if (isPostSuccessRefund && event.fullRefund()) {
            fundingRepository.findById(event.fundingId()).ifPresent(funding -> {
                funding.markRefundedAfterSuccess();
                fundingRepository.save(funding);
            });
        }
    }

    private void restore(CouponIssuance issuance) {
        issuance.restore();
        couponIssuanceRepository.save(issuance);
    }
}
