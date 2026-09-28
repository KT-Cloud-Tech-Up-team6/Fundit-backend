package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.IssueChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * ORDER-012 — 소비자 클레임의 진입점. <b>트랜잭션이 없다.</b>
 *
 * <p>LIVE 게이트 판정(live-service 호출)을 트랜잭션 밖에서 끝내고 발급만
 * {@link CouponIssuanceService#claim}에 넘긴다 — 외부 호출을 트랜잭션 안에서 하면 DB 커넥션을
 * 쥔 채 네트워크 응답을 기다리게 된다. 방송 중 쿠폰 클레임은 동시 요청이 몰리는 지점이라
 * 커넥션 풀이 먼저 마른다. 주문 생성의 라이브 꼬리표 조회를 컨트롤러에서 끝내는 것과 같은 이유다.
 */
@Service
@RequiredArgsConstructor
public class CouponClaimService {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimService.class);

    /** live-service {@code LiveSession.status}의 "방송 중" 값. 문자열인 이유는 서비스 간 계약이기 때문. */
    private static final String LIVE_STATUS = "LIVE";

    private final CouponRepository couponRepository;
    private final LiveStatusClient liveStatusClient;
    private final CouponIssuanceService couponIssuanceService;

    public CouponIssuance claim(UUID memberId, String couponCode) {
        Coupon coupon = couponRepository.findByCouponCode(couponCode)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        requireLiveOngoing(coupon);
        return couponIssuanceService.claim(memberId, couponCode);
    }

    /**
     * LIVE 쿠폰은 방송 중일 때만 받을 수 있다. GENERAL 쿠폰은 live 호출 자체가 발생하지 않는다.
     *
     * <p>쿠폰은 통과/차단을 정하는 <b>게이트</b>라, live 조회 실패(타임아웃/5xx)는 예외를 그대로
     * 전파해 503으로 끝낸다(확정 계약 4번) — 못 믿으면 닫는다. 주문의 라이브ID(꼬리표)와 반대다.
     */
    private void requireLiveOngoing(Coupon coupon) {
        if (coupon.getIssueChannel() != IssueChannel.LIVE) {
            return;
        }
        if (coupon.getLiveSessionId() == null) {
            log.warn("LIVE 쿠폰에 liveSessionId가 없습니다. couponCode={}", coupon.getCouponCode());
            throw new BusinessException(OrderErrorCode.COUPON_NOT_APPLICABLE, "진행 중인 방송이 아닙니다.");
        }
        Optional<LiveStatusClient.LiveStatus> live = liveStatusClient.findBySessionId(coupon.getLiveSessionId());
        if (live.isEmpty()) {
            // live에 없는 세션을 order가 참조 중이라는 뜻 — 데이터 정합성 문제라 경고로 남긴다.
            log.warn("존재하지 않는 방송 세션을 참조하는 LIVE 쿠폰입니다. couponCode={}, liveSessionId={}",
                    coupon.getCouponCode(), coupon.getLiveSessionId());
        } else if (LIVE_STATUS.equals(live.get().status())) {
            return;
        }
        throw new BusinessException(OrderErrorCode.COUPON_NOT_APPLICABLE, "진행 중인 방송이 아닙니다.");
    }
}
