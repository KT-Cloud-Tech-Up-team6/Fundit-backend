package com.fundit.order.domain.coupon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findByCouponCode(String couponCode);

    /** ORDER-009 쿠폰함 조회에서 발급 건마다 쿠폰 템플릿을 한 번에 가져오기 위한 배치 조회. */
    List<Coupon> findByCouponCodeIn(Collection<String> couponCodes);

    Coupon save(Coupon coupon);

    /**
     * ORDER-012 선착순/드롭 쿠폰 클레임 — remaining_quantity를 낙관적 락 조건부 UPDATE로
     * 차감한다(CLAUDE.md "쿠폰 재고 차감도 재고와 동일하게 낙관적 락 + 조건부 UPDATE로 한다").
     *
     * @return true면 차감 성공, false면 (재시도 후에도) 소진으로 최종 실패
     */
    boolean decreaseRemainingQuantity(String couponCode);

    /**
     * ORDER-003/ORDER-008 쿠폰 적용/발급 시 예산 사용액을 조건부 UPDATE로 반영한다.
     * budget_limit이 없는 쿠폰(무제한)에도 사용액 누적 자체는 기록한다.
     */
    boolean increaseUsedBudget(String couponCode, long amount);
}
