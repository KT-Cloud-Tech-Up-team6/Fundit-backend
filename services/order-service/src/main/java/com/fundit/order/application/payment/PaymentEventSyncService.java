package com.fundit.order.application.payment;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentEventSyncService implements PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventSyncService.class);

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
        // 만료 배치(CouponExpirationBatchScheduler)가 이미 EXPIRED로 전이시켰다면 복원하지 않는다 —
        // 유효기간이 지난 쿠폰이 환불을 계기로 되살아나면 안 된다.
        if (issuance.getStatus() == CouponIssuanceStatus.EXPIRED) {
            log.info("이미 만료된 쿠폰 발급 건이라 환불로 복원하지 않습니다. couponIssuanceId={}", issuance.getId());
            return;
        }
        issuance.restore();
        couponIssuanceRepository.save(issuance);
    }
}
