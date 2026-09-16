package com.fundit.order.application.coupon;

import com.fundit.order.application.notification.OrderNotificationPublisher;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 건 한 개에 만료임박 리마인더를 발송 표시하고 알림을 적재한다. 배치 대상 건마다
 * 별도 트랜잭션으로 처리해, 한 건 실패가 이미 처리된 다른 건까지 함께 롤백시키지 않게 한다
 * ({@code CouponExpirationProcessor}와 동일한 방어 스타일).
 */
@Service
@RequiredArgsConstructor
public class CouponExpiringReminderProcessor {

    private final CouponIssuanceRepository couponIssuanceRepository;
    private final OrderNotificationPublisher notificationPublisher;

    /** @return true면 이번 호출로 리마인더를 적재함, false면 이미 처리돼 있었음(idempotent). */
    @Transactional
    public boolean remindOne(Long couponIssuanceId) {
        CouponIssuance issuance = couponIssuanceRepository.findById(couponIssuanceId).orElse(null);
        if (issuance == null || !issuance.isAvailable() || issuance.getExpiringNotifiedAt() != null) {
            return false;
        }
        issuance.markExpiringNotified();
        couponIssuanceRepository.save(issuance);
        notificationPublisher.publishCouponExpiring(
                new CouponExpiringEvent(issuance.getId(), issuance.getOwnerId()));
        return true;
    }
}
