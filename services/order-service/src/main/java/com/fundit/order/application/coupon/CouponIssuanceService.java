package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * ORDER-012(소비자 능동 클레임)/ORDER-007(플랫폼 자동 발급)가 공유하는 발급 핵심 로직.
 * remaining_quantity 차감은 재고와 동일하게 낙관적 락 + 조건부 UPDATE로 한다(CLAUDE.md).
 */
@Service
@RequiredArgsConstructor
public class CouponIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceService.class);

    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;

    /** ORDER-012 — 소비자가 쿠폰코드로 직접 "받기"를 요청한다. */
    @Transactional
    public CouponIssuance claim(UUID memberId, String couponCode) {
        Coupon coupon = couponRepository.findByCouponCode(couponCode)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (coupon.isExpired(Instant.now())) {
            throw new BusinessException(OrderErrorCode.COUPON_NOT_APPLICABLE, "만료된 쿠폰입니다.");
        }
        long alreadyIssued = couponIssuanceRepository.countByCouponCodeAndOwnerId(couponCode, memberId);
        if (alreadyIssued >= coupon.getPerMemberLimit()) {
            throw new BusinessException(OrderErrorCode.COUPON_NOT_APPLICABLE, "1인 발급 한도를 초과했습니다.");
        }
        // issue_channel=LIVE 쿠폰의 "방송 진행 중" 검증은 live-service 연동 전이라 생략한다
        // [가정 — live-service 동기 조회 붙는 대로 후속 보강 필요, OrderDomainApiSpec.md #9 참고].

        return issueOrThrow(coupon, memberId);
    }

    /**
     * ORDER-007 — 시스템 자동 발급(가입/등급/이벤트). 실제 트리거(가입 이벤트 등)는
     * member-service 연동이 아직 없어 이 메서드를 호출하는 리스너는 없다 — 로직만 완성해둔다
     * [가정 — ORDER-016 RewardEventListener와 동일한 "골격 우선" 접근]. per_member_limit을
     * 넘겨 중복 자동발급되지 않도록 동일하게 보호한다.
     */
    @Transactional
    public Optional<CouponIssuance> autoIssue(UUID memberId, String couponCode) {
        Optional<Coupon> couponOpt = couponRepository.findByCouponCode(couponCode);
        if (couponOpt.isEmpty()) {
            log.warn("자동 발급 대상 쿠폰이 존재하지 않습니다. couponCode={}", couponCode);
            return Optional.empty();
        }
        Coupon coupon = couponOpt.get();
        if (coupon.isExpired(Instant.now())) {
            return Optional.empty();
        }
        long alreadyIssued = couponIssuanceRepository.countByCouponCodeAndOwnerId(couponCode, memberId);
        if (alreadyIssued >= coupon.getPerMemberLimit()) {
            return Optional.empty();
        }
        try {
            return Optional.of(issueOrThrow(coupon, memberId));
        } catch (BusinessException e) {
            // 발급 수량/예산 소진 — 요구사항상 "발급 중단"이며 사용자에게 보여줄 요청 흐름이
            // 아니므로 예외를 전파하지 않고 조용히 건너뛴다.
            log.info("자동 발급 중단(소진). couponCode={}, memberId={}", couponCode, memberId);
            return Optional.empty();
        }
    }

    private CouponIssuance issueOrThrow(Coupon coupon, UUID memberId) {
        boolean claimed = couponRepository.decreaseRemainingQuantity(coupon.getCouponCode());
        if (!claimed) {
            throw new BusinessException(OrderErrorCode.COUPON_EXHAUSTED);
        }
        return couponIssuanceRepository.save(CouponIssuance.issue(coupon.getCouponCode(), memberId));
    }
}
