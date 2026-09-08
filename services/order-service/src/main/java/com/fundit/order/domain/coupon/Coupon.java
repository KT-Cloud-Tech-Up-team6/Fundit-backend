package com.fundit.order.domain.coupon;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — remaining_quantity/used_budget_amount에
 * 낙관적 락 + 조건부 UPDATE가 필요한 동시성 불변식이 있다(ORDER-012 선착순 클레임 등).
 * 그래서 이 객체는 재고(Inventory)와 마찬가지로 읽기 모델에 가깝게 불변으로 두고,
 * 실제 수량/예산 증감은 {@link CouponRepository}의 조건부 UPDATE 메서드로만 한다.
 */
@Getter
@Builder(toBuilder = true)
public class Coupon {

    private final Long id;
    private final String couponCode;
    private final String couponName;
    private final DiscountType discountType;
    private final long discountValue;
    private final Long maxDiscountAmount;
    private final Long budgetLimit;
    private final long usedBudgetAmount;
    private final IssuerType issuerType;
    private final UUID issuerId;
    private final CouponTargetScope targetScope;
    private final String targetRefId;
    private final long minFundingAmount;
    private final int perMemberLimit;
    private final int remainingQuantity;
    private final Instant expiresAt;
    private final IssueChannel issueChannel;
    private final Long liveSessionId;
    private final DropType dropType;
    private final int version;
    private final Instant createdAt;

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean meetsMinFundingAmount(long rewardAmount) {
        return rewardAmount >= minFundingAmount;
    }

    /**
     * targetScope=PROJECT/CATEGORY/MAKER 매칭 검증. project-service 조회 없이 판단 가능한
     * ALL/PROJECT만 정확히 검증하고, CATEGORY/MAKER는 이번 MVP 범위에서는 항상 적용 가능한
     * 것으로 취급한다[가정 — project-service의 카테고리/판매자 조회가 필요해 후속 보강 필요].
     */
    public boolean matchesProject(Long projectId) {
        return switch (targetScope) {
            case ALL, CATEGORY, MAKER -> true;
            case PROJECT -> targetRefId != null && targetRefId.equals(String.valueOf(projectId));
        };
    }

    /** RATE/AMOUNT/FREE_SHIPPING 할인액 계산, maxDiscountAmount·주문 총액 한도로 캡핑한다. */
    public long calculateDiscount(long rewardAmount, long shippingFee) {
        long raw = switch (discountType) {
            case RATE -> Math.floorDiv(rewardAmount * discountValue, 100);
            case AMOUNT -> discountValue;
            case FREE_SHIPPING -> shippingFee;
        };
        long capped = maxDiscountAmount != null ? Math.min(raw, maxDiscountAmount) : raw;
        long upperBound = discountType == DiscountType.FREE_SHIPPING ? shippingFee : rewardAmount + shippingFee;
        return Math.max(0, Math.min(capped, upperBound));
    }

    public boolean hasRemainingBudget(long expectedDiscount) {
        if (budgetLimit == null) {
            return true;
        }
        return usedBudgetAmount + expectedDiscount <= budgetLimit;
    }
}
