package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import com.fundit.order.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** ORDER-009 — 쿠폰함 조회. 본인 보유 쿠폰만 조회한다(S4). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponBoxQueryService {

    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponRepository couponRepository;

    public Page<CouponBoxItem> list(UUID memberId, CouponIssuanceStatus status, Pageable pageable) {
        Page<CouponIssuance> issuances = couponIssuanceRepository.findByOwnerId(memberId, status, pageable);

        Map<String, Coupon> couponsByCode = couponRepository
                .findByCouponCodeIn(issuances.getContent().stream().map(CouponIssuance::getCouponCode).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Coupon::getCouponCode, Function.identity()));

        return issuances.map(issuance -> new CouponBoxItem(couponsByCode.get(issuance.getCouponCode()), issuance));
    }

    public record CouponBoxItem(Coupon coupon, CouponIssuance issuance) {
    }
}
