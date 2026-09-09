package com.fundit.order.application.coupon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** MemberLifecycleEventListener 클래스 주석 참고 — 신규가입 시 웰컴 쿠폰 1종만 대표 배선. */
@Service
public class WelcomeCouponAutoIssueService implements MemberLifecycleEventListener {

    private final CouponIssuanceService couponIssuanceService;
    private final String welcomeCouponCode;

    public WelcomeCouponAutoIssueService(CouponIssuanceService couponIssuanceService,
                                          @Value("${order.policy.welcome-coupon-code:}") String welcomeCouponCode) {
        this.couponIssuanceService = couponIssuanceService;
        this.welcomeCouponCode = welcomeCouponCode;
    }

    @Override
    public void onMemberSignedUp(MemberSignedUpEvent event) {
        if (welcomeCouponCode == null || welcomeCouponCode.isBlank()) {
            return; // 웰컴 쿠폰 코드가 설정되지 않았으면 자동 발급하지 않는다.
        }
        couponIssuanceService.autoIssue(event.memberId(), welcomeCouponCode);
    }
}
