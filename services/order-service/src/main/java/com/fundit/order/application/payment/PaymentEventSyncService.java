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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentEventSyncService implements PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventSyncService.class);

    private final FundingRepository fundingRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Optional<Funding> found = fundingRepository.findByPublicId(event.fundingId());
        if (found.isEmpty()) {
            log.warn("결제완료 이벤트의 주문을 찾을 수 없습니다. fundingId={}", event.fundingId());
            return;
        }
        Funding funding = found.get();
        funding.markPaymentCompleted();
        fundingRepository.save(funding);

        for (Long couponIssuanceId : orEmpty(event.couponIssuanceIds())) {
            couponIssuanceRepository.findById(couponIssuanceId).ifPresent(issuance -> {
                if (issuance.isAvailable()) {
                    issuance.markUsed(funding.getId());
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
            // 구매자 귀책 반품은 주문이 실제로 성립·발송까지 갔던 건이라 쿠폰을 되돌리지 않는다.
            case CANCELLED_BY_MEMBER, POST_SUCCESS_RETURN -> false;
            case POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY -> event.fullRefund();
        };

        if (shouldRestoreCoupon) {
            for (Long couponIssuanceId : orEmpty(event.couponIssuanceIds())) {
                couponIssuanceRepository.findById(couponIssuanceId).ifPresent(this::restore);
            }
        }

        // 하자·지연은 전액 환불된 건만 주문을 되돌린다(부분 환불이면 주문은 그대로 유효).
        // 반품은 반품비가 차감된 부분 환불이어도 "반품 완료"라 주문 상태를 전이시킨다.
        boolean refundVoidsOrder = switch (event.refundReason()) {
            case POST_SUCCESS_RETURN -> true;
            case POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY -> event.fullRefund();
            case GOAL_FAILURE_AUTO_REFUND, CANCELLED_BY_MEMBER -> false;
        };
        if (refundVoidsOrder) {
            fundingRepository.findByPublicId(event.fundingId()).ifPresent(funding -> {
                funding.markRefundedAfterSuccess();
                fundingRepository.save(funding);
            });
        }
    }

    private List<Long> orEmpty(List<Long> couponIssuanceIds) {
        return couponIssuanceIds == null ? List.of() : couponIssuanceIds;
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
