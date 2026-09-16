package com.fundit.order.domain.coupon;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트 — AVAILABLE/USED/EXPIRED 상태 전이 규칙이 있다. remaining_quantity 같은
 * 공유 카운터가 아니라 회원별 개별 발급 건이라 동시 경쟁이 없으므로(같은 발급 건을 여러 스레드가
 * 동시에 쓰는 상황은 없음) 일반적인 로드→변경→save() 패턴을 쓴다.
 */
@Getter
@Builder(toBuilder = true)
public class CouponIssuance {

    private final Long id;
    private final String couponCode;
    private final UUID ownerId;
    private final Instant issuedAt;
    private CouponIssuanceStatus status;
    private Long usedFundingId;
    private Instant usedAt;
    private Instant restoredAt;
    private Instant expiringNotifiedAt;

    public static CouponIssuance issue(String couponCode, UUID ownerId) {
        return CouponIssuance.builder()
                .couponCode(couponCode)
                .ownerId(ownerId)
                .status(CouponIssuanceStatus.AVAILABLE)
                .build();
    }

    public boolean isOwnedBy(UUID memberId) {
        return ownerId.equals(memberId);
    }

    public boolean isAvailable() {
        return status == CouponIssuanceStatus.AVAILABLE;
    }

    /** ORDER-003/ORDER-015 — 결제완료 시 사용완료 처리. */
    public void markUsed(Long fundingId) {
        this.status = CouponIssuanceStatus.USED;
        this.usedFundingId = fundingId;
        this.usedAt = Instant.now();
    }

    /** ORDER-015 — 환불 유형에 따라 사용 이력을 복원(재사용 가능 상태로). */
    public void restore() {
        this.status = CouponIssuanceStatus.AVAILABLE;
        this.usedFundingId = null;
        this.restoredAt = Instant.now();
    }

    /** @return true면 이번 호출로 만료 처리됨, false면 이미 AVAILABLE이 아니었음(idempotent). */
    public boolean expireIfAvailable() {
        if (status != CouponIssuanceStatus.AVAILABLE) {
            return false;
        }
        this.status = CouponIssuanceStatus.EXPIRED;
        return true;
    }

    /** 만료임박 리마인더 발송 표시(중복 알림 방지) — 만료 상태 전이와는 별개다. */
    public void markExpiringNotified() {
        this.expiringNotifiedAt = Instant.now();
    }
}
