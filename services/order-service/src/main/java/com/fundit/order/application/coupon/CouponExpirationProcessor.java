package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 건 한 개를 만료 처리한다. 배치 대상 건마다 별도 트랜잭션으로 처리해, 한 건 실패가
 * 이미 처리된 다른 건까지 함께 롤백시키지 않게 한다(order-service {@code PaymentExpirationProcessor}와
 * 동일한 방어 스타일).
 */
@Service
@RequiredArgsConstructor
public class CouponExpirationProcessor {

    private final CouponIssuanceRepository couponIssuanceRepository;

    /** @return true면 이번 호출로 만료 처리됨, false면 이미 처리돼 있었음(idempotent). */
    @Transactional
    public boolean expireOne(Long couponIssuanceId) {
        // 배치가 대상 목록을 조회한 시점과 처리 시점 사이에 상태가 바뀌었을 수 있어 매번 최신값을 다시 읽는다.
        CouponIssuance issuance = couponIssuanceRepository.findById(couponIssuanceId).orElse(null);
        if (issuance == null) {
            return false;
        }
        boolean expired = issuance.expireIfAvailable();
        if (!expired) {
            return false;
        }
        couponIssuanceRepository.save(issuance);
        return true;
    }
}
